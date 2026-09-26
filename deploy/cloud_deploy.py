#!/usr/bin/env python3
"""
Put TableTap online on Azure or AWS, and connect the GitHub pipeline so every push to main deploys.

    python3 deploy/cloud_deploy.py azure            # set up / update on Azure
    python3 deploy/cloud_deploy.py aws              # set up / update on AWS
    python3 deploy/cloud_deploy.py azure --delete   # remove everything it created
    python3 deploy/cloud_deploy.py aws --delete

Before running: enable the pipeline (`git mv github .github`, commit, push), log in to the cloud CLI
(`az login` or `aws configure` / `aws sso login`), and have a GitHub token ready (the script
explains which). Only the Python standard library is used. Safe to re-run: existing resources
are reused and updated.

Azure:  GitHub Container Registry (image) -> Azure Container Apps (scales to zero)
        + Azure Database for PostgreSQL Flexible Server (Standard_B1ms)
AWS:    Amazon ECR (image) -> AWS App Runner (+ VPC connector)
        + Amazon RDS for PostgreSQL (db.t4g.micro), secrets in SSM Parameter Store
"""

import argparse
import base64
import getpass
import hashlib
import json
import os
import re
import secrets
import shutil
import string
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE_FILE = Path(__file__).with_name(".cloud-state.json")
WORKFLOW_PATH = ".github/workflows/deploy.yml"
WORKFLOW_FILE = "deploy.yml"
APP = "tabletap"
DB_USER = "tabletapadmin"


# ============================================================== small helpers

def step(msg):
    print(f"\n==> {msg}", flush=True)


def info(msg):
    print(f"    {msg}", flush=True)


def fail(msg):
    print(f"\nERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def ask_yes(question, default=False):
    answer = input(f"{question} [{'Y/n' if default else 'y/N'}] ").strip().lower()
    return default if not answer else answer in ("y", "yes")


def strong_password(length=24):
    alphabet = string.ascii_letters + string.digits
    while True:
        pw = "".join(secrets.choice(alphabet) for _ in range(length))
        if any(c.isdigit() for c in pw) and any(c.isalpha() for c in pw):
            return pw


def load_state():
    return json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else {}


def save_state(state):
    STATE_FILE.write_text(json.dumps(state, indent=2))
    os.chmod(STATE_FILE, 0o600)  # holds generated passwords: readable only by you


def run(tool, *args, parse=True, check=True, quiet_args=False):
    """Run a CLI (no shell involved). quiet_args=True keeps arguments out of error messages."""
    exe = shutil.which(tool) or shutil.which(f"{tool}.cmd")
    if not exe:
        fail(f"'{tool}' command not found. Install the {'Azure' if tool == 'az' else 'AWS'} CLI first.")
    cmd = [exe, *args]
    if parse:
        cmd += ["--output", "json"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        if not check:
            return None
        shown = " ".join(args[:3]) if quiet_args else " ".join(args)
        fail(f"{tool} {shown}\n{result.stderr.strip()}")
    out = result.stdout.strip()
    if not parse:
        return out
    return json.loads(out) if out else {}


def az(*args, **kw):
    return run("az", *args, "--only-show-errors", **kw)


def aws(*args, **kw):
    return run("aws", *args, **kw)


def aws_input(*args, payload, **kw):
    """Pass a JSON document to the AWS CLI via a private temp file (keeps secrets out of argv)."""
    fd, path = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w") as f:
            json.dump(payload, f)
        return aws(*args, "--cli-input-json", f"file://{path}", **kw)
    finally:
        os.remove(path)


def wait_healthy(url, minutes=10):
    step(f"Waiting for {url} to answer (the first start can take a few minutes)")
    deadline = time.time() + minutes * 60
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"{url}/actuator/health", timeout=20) as r:
                if b'"UP"' in r.read():
                    print()
                    return True
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        print(".", end="", flush=True)
        time.sleep(10)
    print()
    return False


# ============================================================== GitHub

class GitHub:
    def __init__(self, token, repo):
        self.token, self.repo = token, repo

    def call(self, method, path, body=None):
        req = urllib.request.Request(
            f"https://api.github.com{path}", method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "tabletap-deploy"})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as e:
            raise GitHubError(e.code, e.read().decode(errors="replace")[:300]) from None

    def set_variable(self, name, value):
        try:
            self.call("POST", f"/repos/{self.repo}/actions/variables", {"name": name, "value": value})
        except GitHubError as e:
            if e.code != 409:  # already exists -> update
                raise
            self.call("PATCH", f"/repos/{self.repo}/actions/variables/{name}", {"name": name, "value": value})


class GitHubError(Exception):
    def __init__(self, code, detail):
        super().__init__(f"GitHub API {code}: {detail}")
        self.code = code


def token_login(token, needed, label):
    req = urllib.request.Request("https://api.github.com/user",
                                 headers={"Authorization": f"Bearer {token}", "User-Agent": "tabletap-deploy"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            login = json.loads(resp.read())["login"]
            scopes = {s.strip() for s in (resp.headers.get("X-OAuth-Scopes") or "").split(",") if s.strip()}
    except urllib.error.HTTPError:
        fail(f"GitHub rejected {label}. Check you copied it correctly.")
    missing = [s for s in needed if s not in scopes and not (s == "read:packages" and "write:packages" in scopes)]
    if scopes and missing:
        fail(f"{label} is missing these scopes: {', '.join(missing)}")
    return login


def detect_repo():
    try:
        url = subprocess.run(["git", "-C", str(ROOT), "remote", "get-url", "origin"],
                             capture_output=True, text=True, check=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    m = re.search(r"github\.com[:/](.+?/.+?)(?:\.git)?$", url)
    return m.group(1) if m else None


def connect_github(args, state):
    repo = args.repo or state.get("repo") or detect_repo()
    if not repo:
        fail("Couldn't detect the GitHub repo. Pass --repo your-user/tabletap")
    step(f"GitHub repo {repo}")
    print("    Needs a classic token (https://github.com/settings/tokens/new) with scopes: repo, workflow.")
    print("    It is only used while this script runs and is never saved.")
    token = getpass.getpass("    Paste the GitHub token (hidden): ").strip()
    user = token_login(token, ["repo", "workflow"], "The GitHub token")
    gh = GitHub(token, repo)
    try:
        branch = gh.call("GET", f"/repos/{repo}")["default_branch"]
    except GitHubError:
        fail(f"The token can't access {repo}.")
    try:
        gh.call("GET", f"/repos/{repo}/contents/{WORKFLOW_PATH}?ref={branch}")
    except GitHubError as e:
        if e.code == 404:
            fail(f"{WORKFLOW_PATH} isn't on GitHub yet. Enable it first:\n"
                 "    git mv github .github && git commit -m \"Enable deploy pipeline\" && git push")
        raise
    state["repo"] = repo
    return gh, user, branch


def run_pipeline(gh, branch, wait_for_build_only):
    """Start the workflow and wait. Before the app exists it only builds and pushes the image."""
    step("Running the GitHub pipeline (build + push the image, about 5 minutes)")
    started = time.time()
    gh.call("POST", f"/repos/{gh.repo}/actions/workflows/{WORKFLOW_FILE}/dispatches", {"ref": branch})
    run_ = None
    for _ in range(30):
        time.sleep(4)
        runs = gh.call("GET", f"/repos/{gh.repo}/actions/workflows/{WORKFLOW_FILE}/runs?event=workflow_dispatch&per_page=5")
        for r in runs["workflow_runs"]:
            created = time.mktime(time.strptime(r["created_at"], "%Y-%m-%dT%H:%M:%SZ")) - time.timezone
            if created >= started - 60:
                run_ = r
                break
        if run_:
            break
    if not run_:
        fail("Couldn't find the pipeline run. Check the Actions tab on GitHub.")
    info(f"Follow along: {run_['html_url']}")
    deadline = time.time() + 25 * 60
    while time.time() < deadline:
        r = gh.call("GET", f"/repos/{gh.repo}/actions/runs/{run_['id']}")
        if r["status"] == "completed":
            if r["conclusion"] != "success":
                fail(f"The pipeline failed. Open {run_['html_url']} to see why.")
            info("Pipeline finished." if not wait_for_build_only else "Image built and pushed.")
            return
        time.sleep(10)
    fail(f"The pipeline is taking too long. Check {run_['html_url']}")


def common_secrets(state, args):
    if "admin_email" not in state:
        email = args.admin_email or input("\nEmail for the TableTap admin account: ").strip()
        if not re.match(r"^[^@\s]+@[^@\s]+\.[^@\s]+$", email):
            fail("That doesn't look like an email address.")
        state.update(admin_email=email, admin_password=strong_password(20),
                     db_password=strong_password(28), jwt_secret=secrets.token_urlsafe(64))
        save_state(state)
        info(f"Passwords generated and saved to {STATE_FILE.relative_to(ROOT)} (git-ignored, only you can read it)")


def app_env(state, db_host):
    """Environment variables the Spring Boot app reads (see application.yml). Passwords are added separately."""
    return {
        "DB_URL": f"jdbc:postgresql://{db_host}:5432/tabletap?sslmode=require",
        "DB_USER": DB_USER,
        "ADMIN_EMAIL": state["admin_email"],
        "DEMO_DATA": "false",
        "JAVA_OPTS": "-XX:MaxRAMPercentage=70",
    }


def print_done(state, url, healthy, target):
    print("\n" + "=" * 66)
    print(f"  TableTap on {target}: {url}")
    print(f"  Admin login:     {state['admin_email']}")
    print(f"  Admin password:  {state['admin_password']}")
    print("=" * 66)
    if not healthy:
        print("It hasn't answered yet. Give it a few minutes, then check the logs in the cloud console.")
    print("\nEvery push to main now builds and deploys automatically (.github/workflows/deploy.yml).")
    print("You can delete the GitHub token you pasted.")


# ============================================================== Azure

def azure_deploy(args, state):
    account = az("account", "show", check=False)
    if not account:
        info("Opening the Azure login page…")
        subprocess.run([shutil.which("az"), "login", "--only-show-errors", "--output", "none"], check=True)
        account = az("account", "show")
    step(f"Azure subscription: {account['name']}")
    if not ask_yes("Create/update TableTap here?", default=True):
        sys.exit(0)

    gh, gh_user, branch = connect_github(args, state)
    print("\n    Azure also needs a second classic token with ONLY the scope read:packages,")
    print("    so it can download your private image from ghcr.io. It is stored as a Container App")
    print("    secret. Pick a long expiry: when it expires, Azure can't start the app.")
    pull_token = getpass.getpass("    Paste the read:packages token (hidden): ").strip()
    token_login(pull_token, ["read:packages"], "The read:packages token")
    common_secrets(state, args)

    rg, loc = args.resource_group, args.location
    suffix = hashlib.sha1(f"{account['id']}{rg}".encode()).hexdigest()[:6]
    pg, env_name = f"{APP}-pg-{suffix}", f"{APP}-env"
    state.update(target="azure", rg=rg, location=loc, pg=pg)
    save_state(state)

    step("Enabling Azure services (first time only)")
    for ns in ("Microsoft.App", "Microsoft.DBforPostgreSQL", "Microsoft.OperationalInsights"):
        az("provider", "register", "-n", ns, "--wait", parse=False)
    az("extension", "add", "--name", "containerapp", "--upgrade", "--yes", parse=False)

    step(f"Resource group {rg} ({loc})")
    az("group", "create", "-n", rg, "-l", loc, "--tags", f"app={APP}")

    step(f"PostgreSQL {pg} (Standard_B1ms: included in the free account for 12 months)")
    if az("postgres", "flexible-server", "show", "-g", rg, "-n", pg, check=False):
        info("Already exists — reusing it.")
    else:
        info("Creating (5–10 minutes)…")
        az("postgres", "flexible-server", "create", "-g", rg, "-n", pg, "-l", loc,
           "--tier", "Burstable", "--sku-name", "Standard_B1ms", "--storage-size", "32", "--version", "16",
           "--admin-user", DB_USER, "--admin-password", state["db_password"],
           "--public-access", "0.0.0.0",  # reachable only from inside Azure; TLS required
           "--backup-retention", "7", "--high-availability", "Disabled", "--yes", quiet_args=True)
    if not az("postgres", "flexible-server", "db", "show", "-g", rg, "-s", pg, "-d", "tabletap", check=False):
        az("postgres", "flexible-server", "db", "create", "-g", rg, "-s", pg, "-d", "tabletap")
    db_host = az("postgres", "flexible-server", "show", "-g", rg, "-n", pg)["fullyQualifiedDomainName"]

    step(f"Container Apps environment {env_name}")
    if not az("containerapp", "env", "show", "-g", rg, "-n", env_name, check=False):
        az("containerapp", "env", "create", "-g", rg, "-n", env_name, "-l", loc, "--logs-destination", "none")

    # GitHub -> Azure trust (OpenID Connect), scoped to this resource group only
    step("Letting the GitHub pipeline deploy to Azure (no stored passwords)")
    name = f"{APP}-github-{gh.repo.replace('/', '-')}"
    apps = az("ad", "app", "list", "--display-name", name)
    app_id = apps[0]["appId"] if apps else az("ad", "app", "create", "--display-name", name)["appId"]
    if not az("ad", "sp", "show", "--id", app_id, check=False):
        az("ad", "sp", "create", "--id", app_id)
        time.sleep(15)
    sp_id = az("ad", "sp", "show", "--id", app_id)["id"]
    subject = f"repo:{gh.repo}:ref:refs/heads/{branch}"
    if not any(c.get("subject") == subject for c in az("ad", "app", "federated-credential", "list", "--id", app_id) or []):
        az("ad", "app", "federated-credential", "create", "--id", app_id, "--parameters", json.dumps({
            "name": "github-" + hashlib.sha1(subject.encode()).hexdigest()[:10],
            "issuer": "https://token.actions.githubusercontent.com",
            "subject": subject, "audiences": ["api://AzureADTokenExchange"]}))
    rg_id = az("group", "show", "-n", rg)["id"]
    if not any(a.get("roleDefinitionName") == "Contributor"
               for a in az("role", "assignment", "list", "--assignee", sp_id, "--scope", rg_id) or []):
        for _ in range(6):
            if az("role", "assignment", "create", "--assignee-object-id", sp_id,
                  "--assignee-principal-type", "ServicePrincipal", "--role", "Contributor",
                  "--scope", rg_id, check=False) is not None:
                break
            time.sleep(10)
        else:
            fail("Couldn't give the pipeline access to the resource group.")

    for k, v in {"DEPLOY_TARGET": "azure", "AZURE_CLIENT_ID": app_id, "AZURE_TENANT_ID": account["tenantId"],
                 "AZURE_SUBSCRIPTION_ID": account["id"], "AZURE_RESOURCE_GROUP": rg,
                 "AZURE_CONTAINER_APP": APP}.items():
        gh.set_variable(k, v)
    info("Pipeline variables saved on GitHub.")

    app_exists = bool(az("containerapp", "show", "-g", rg, "-n", APP, check=False))
    if not args.skip_build:
        run_pipeline(gh, branch, wait_for_build_only=not app_exists)

    step(f"Container App {APP} (scales to zero when idle, max 1 copy)")
    image = f"ghcr.io/{gh.repo.split('/')[0].lower()}/{APP}:latest"
    secret_args = [f"db-password={state['db_password']}", f"jwt-secret={state['jwt_secret']}",
                   f"admin-password={state['admin_password']}"]
    env_args = [f"{k}={v}" for k, v in app_env(state, db_host).items()] + [
        "DB_PASSWORD=secretref:db-password", "JWT_SECRET=secretref:jwt-secret", "ADMIN_PASSWORD=secretref:admin-password"]
    if app_exists:
        az("containerapp", "secret", "set", "-g", rg, "-n", APP, "--secrets", *secret_args, quiet_args=True)
        az("containerapp", "registry", "set", "-g", rg, "-n", APP, "--server", "ghcr.io",
           "--username", gh_user, "--password", pull_token, quiet_args=True)
        az("containerapp", "update", "-g", rg, "-n", APP, "--set-env-vars", *env_args)
    else:
        az("containerapp", "create", "-g", rg, "-n", APP, "--environment", env_name, "--image", image,
           "--registry-server", "ghcr.io", "--registry-username", gh_user, "--registry-password", pull_token,
           "--target-port", "8080", "--ingress", "external", "--cpu", "0.5", "--memory", "1.0Gi",
           "--min-replicas", "0", "--max-replicas", "1",  # 1: live updates are held in memory
           "--secrets", *secret_args, "--env-vars", *env_args, quiet_args=True)
    fqdn = az("containerapp", "show", "-g", rg, "-n", APP)["properties"]["configuration"]["ingress"]["fqdn"]
    url = f"https://{fqdn}"
    print_done(state, url, wait_healthy(url), "Azure")


def azure_delete(args, state):
    rg = state.get("rg", args.resource_group)
    step(f"Delete Azure resource group {rg}")
    print("    This permanently deletes the database, the app and all their data.")
    if input(f"    Type {rg} to confirm: ").strip() != rg:
        fail("Not deleted.")
    az("group", "delete", "-n", rg, "--yes", "--no-wait", parse=False)
    info("Deletion started. (The GitHub variables and the app registration are left in place.)")


# ============================================================== AWS

def aws_deploy(args, state):
    region = args.region
    ident = aws("sts", "get-caller-identity", check=False)
    if not ident:
        fail("Not logged in to AWS. Run `aws configure` (or `aws sso login`) first.")
    acct = ident["Account"]
    step(f"AWS account {acct} ({ident['Arn']}), region {region}")
    if not ask_yes("Create/update TableTap here?", default=True):
        sys.exit(0)

    gh, _, branch = connect_github(args, state)
    common_secrets(state, args)
    state.update(target="aws", region=region)
    save_state(state)
    R = ["--region", region]

    # ---- image registry
    step("ECR repository (stores the app image)")
    if not aws("ecr", "describe-repositories", "--repository-names", APP, *R, check=False):
        aws("ecr", "create-repository", "--repository-name", APP, "--image-scanning-configuration",
            "scanOnPush=true", *R)

    # ---- GitHub -> AWS trust (OpenID Connect)
    step("Letting the GitHub pipeline deploy to AWS (no stored passwords)")
    oidc_arn = f"arn:aws:iam::{acct}:oidc-provider/token.actions.githubusercontent.com"
    providers = [p["Arn"] for p in aws("iam", "list-open-id-connect-providers")["OpenIDConnectProviderList"]]
    if oidc_arn not in providers:
        aws("iam", "create-open-id-connect-provider", "--url", "https://token.actions.githubusercontent.com",
            "--client-id-list", "sts.amazonaws.com",
            "--thumbprint-list", "6938fd4d98bab03faadb97b34396831e3780aea1")
    role = f"{APP}-github-deploy"
    trust = {"Version": "2012-10-17", "Statement": [{
        "Effect": "Allow", "Principal": {"Federated": oidc_arn}, "Action": "sts:AssumeRoleWithWebIdentity",
        "Condition": {"StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
                                       "token.actions.githubusercontent.com:sub": f"repo:{gh.repo}:ref:refs/heads/{branch}"}}}]}
    ensure_role(role, trust)
    aws("iam", "put-role-policy", "--role-name", role, "--policy-name", "deploy", "--policy-document", json.dumps({
        "Version": "2012-10-17", "Statement": [
            {"Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*"},
            {"Effect": "Allow", "Resource": f"arn:aws:ecr:{region}:{acct}:repository/{APP}", "Action": [
                "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:CompleteLayerUpload",
                "ecr:GetDownloadUrlForLayer", "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart"]},
            {"Effect": "Allow", "Action": ["apprunner:ListServices"], "Resource": "*"},
            {"Effect": "Allow", "Action": ["apprunner:StartDeployment", "apprunner:DescribeService"],
             "Resource": f"arn:aws:apprunner:{region}:{acct}:service/{APP}/*"}]}), parse=False)

    for k, v in {"DEPLOY_TARGET": "aws", "AWS_ROLE_ARN": f"arn:aws:iam::{acct}:role/{role}", "AWS_REGION": region,
                 "AWS_ECR_REPOSITORY": APP, "AWS_APPRUNNER_SERVICE": APP}.items():
        gh.set_variable(k, v)
    info("Pipeline variables saved on GitHub.")

    # ---- network: default VPC, one security group for the app, one for the database
    step("Network (default VPC and security groups)")
    vpcs = aws("ec2", "describe-vpcs", "--filters", "Name=isDefault,Values=true", *R)["Vpcs"]
    if not vpcs:
        fail("This region has no default VPC. Create one with: aws ec2 create-default-vpc --region " + region)
    vpc = vpcs[0]["VpcId"]
    subnets = [s["SubnetId"] for s in aws("ec2", "describe-subnets", "--filters", f"Name=vpc-id,Values={vpc}", *R)["Subnets"]]
    app_sg = ensure_sg(f"{APP}-app", "TableTap App Runner connector", vpc, R)
    db_sg = ensure_sg(f"{APP}-db", "TableTap database: only from the app", vpc, R)
    aws("ec2", "authorize-security-group-ingress", "--group-id", db_sg, "--protocol", "tcp", "--port", "5432",
        "--source-group", app_sg, *R, check=False)  # fails harmlessly if the rule exists

    # ---- database (started now, finished while the image builds)
    step("RDS PostgreSQL (db.t4g.micro, private, encrypted, 7-day backups)")
    db_id = f"{APP}-db"
    db = aws("rds", "describe-db-instances", "--db-instance-identifier", db_id, *R, check=False)
    if db:
        info("Already exists — reusing it.")
    else:
        info("Creating (about 10 minutes)…")
        aws_input("rds", "create-db-instance", *R, payload={
            "DBInstanceIdentifier": db_id, "Engine": "postgres", "DBInstanceClass": "db.t4g.micro",
            "AllocatedStorage": 20, "StorageType": "gp3", "StorageEncrypted": True,
            "MasterUsername": DB_USER, "MasterUserPassword": state["db_password"], "DBName": APP,
            "VpcSecurityGroupIds": [db_sg], "PubliclyAccessible": False,
            "BackupRetentionPeriod": 7, "MultiAZ": False, "Tags": [{"Key": "app", "Value": APP}]})

    # ---- secrets for the app (SSM Parameter Store, encrypted)
    step("Storing passwords in SSM Parameter Store (encrypted)")
    params = {"DB_PASSWORD": state["db_password"], "JWT_SECRET": state["jwt_secret"],
              "ADMIN_PASSWORD": state["admin_password"]}
    for k, v in params.items():
        aws_input("ssm", "put-parameter", *R, payload={
            "Name": f"/{APP}/{k}", "Value": v, "Type": "SecureString", "Overwrite": True}, parse=False)

    # ---- roles App Runner uses: pull from ECR, read the SSM parameters
    ecr_role = f"{APP}-apprunner-ecr"
    ensure_role(ecr_role, service_trust("build.apprunner.amazonaws.com"))
    aws("iam", "attach-role-policy", "--role-name", ecr_role, "--policy-arn",
        "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess", parse=False)
    inst_role = f"{APP}-apprunner-instance"
    ensure_role(inst_role, service_trust("tasks.apprunner.amazonaws.com"))
    aws("iam", "put-role-policy", "--role-name", inst_role, "--policy-name", "read-secrets", "--policy-document",
        json.dumps({"Version": "2012-10-17", "Statement": [
            {"Effect": "Allow", "Action": ["ssm:GetParameters"],
             "Resource": f"arn:aws:ssm:{region}:{acct}:parameter/{APP}/*"},
            {"Effect": "Allow", "Action": ["kms:Decrypt"], "Resource": "*",
             "Condition": {"StringEquals": {"kms:ViaService": f"ssm.{region}.amazonaws.com"}}}]}), parse=False)

    # ---- VPC connector so App Runner can reach the private database
    step("App Runner VPC connector")
    conns = aws("apprunner", "list-vpc-connectors", *R)["VpcConnectors"]
    conn = next((c for c in conns if c["VpcConnectorName"] == APP and c["Status"] == "ACTIVE"), None)
    if not conn:
        conn = aws("apprunner", "create-vpc-connector", "--vpc-connector-name", APP, "--subnets", *subnets,
                   "--security-groups", app_sg, *R)["VpcConnector"]

    # ---- image
    service = find_apprunner(R)
    if not args.skip_build:
        run_pipeline(gh, branch, wait_for_build_only=service is None)

    step("Waiting for the database to be ready")
    aws("rds", "wait", "db-instance-available", "--db-instance-identifier", db_id, *R, parse=False)
    db_host = aws("rds", "describe-db-instances", "--db-instance-identifier", db_id, *R)["DBInstances"][0]["Endpoint"]["Address"]

    # ---- App Runner service
    step("App Runner service")
    image = f"{acct}.dkr.ecr.{region}.amazonaws.com/{APP}:latest"
    source = {
        "ImageRepository": {
            "ImageIdentifier": image, "ImageRepositoryType": "ECR",
            "ImageConfiguration": {
                "Port": "8080",
                "RuntimeEnvironmentVariables": app_env(state, db_host),
                "RuntimeEnvironmentSecrets": {k: f"arn:aws:ssm:{region}:{acct}:parameter/{APP}/{k}" for k in params}}},
        "AutoDeploymentsEnabled": False,  # the GitHub pipeline triggers deployments
        "AuthenticationConfiguration": {"AccessRoleArn": f"arn:aws:iam::{acct}:role/{ecr_role}"}}
    if service:
        info("Already exists — updating settings.")
        aws_input("apprunner", "update-service", *R, payload={"ServiceArn": service, "SourceConfiguration": source})
    else:
        time.sleep(10)  # new IAM roles take a few seconds to be usable
        service = aws_input("apprunner", "create-service", *R, payload={
            "ServiceName": APP, "SourceConfiguration": source,
            "InstanceConfiguration": {"Cpu": "1 vCPU", "Memory": "2 GB",
                                      "InstanceRoleArn": f"arn:aws:iam::{acct}:role/{inst_role}"},
            "HealthCheckConfiguration": {"Protocol": "HTTP", "Path": "/actuator/health", "Interval": 10,
                                         "Timeout": 5, "HealthyThreshold": 1, "UnhealthyThreshold": 5},
            "NetworkConfiguration": {"EgressConfiguration": {"EgressType": "VPC",
                                                             "VpcConnectorArn": conn["VpcConnectorArn"]}},
            "Tags": [{"Key": "app", "Value": APP}]})["Service"]["ServiceArn"]
    state["apprunner_arn"] = service
    save_state(state)

    info("Starting (5–10 minutes)…")
    for _ in range(90):
        svc = aws("apprunner", "describe-service", "--service-arn", service, *R)["Service"]
        if svc["Status"] == "RUNNING":
            break
        if svc["Status"] in ("CREATE_FAILED", "DELETED"):
            fail(f"App Runner status {svc['Status']}. See the service's logs in the AWS console.")
        time.sleep(10)
    url = f"https://{svc['ServiceUrl']}"
    print_done(state, url, wait_healthy(url), "AWS")


def service_trust(principal):
    return {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Principal": {"Service": principal},
                                                    "Action": "sts:AssumeRole"}]}


def ensure_role(name, trust):
    if aws("iam", "get-role", "--role-name", name, check=False):
        aws("iam", "update-assume-role-policy", "--role-name", name, "--policy-document", json.dumps(trust), parse=False)
    else:
        aws("iam", "create-role", "--role-name", name, "--assume-role-policy-document", json.dumps(trust),
            "--tags", f"Key=app,Value={APP}")


def ensure_sg(name, desc, vpc, R):
    found = aws("ec2", "describe-security-groups", "--filters", f"Name=group-name,Values={name}",
                f"Name=vpc-id,Values={vpc}", *R)["SecurityGroups"]
    if found:
        return found[0]["GroupId"]
    return aws("ec2", "create-security-group", "--group-name", name, "--description", desc, "--vpc-id", vpc, *R)["GroupId"]


def find_apprunner(R):
    arn = aws("apprunner", "list-services", "--query",
              f"ServiceSummaryList[?ServiceName=='{APP}'].ServiceArn | [0]", *R)
    return arn if isinstance(arn, str) and arn else None


def aws_delete(args, state):
    region = state.get("region", args.region)
    R = ["--region", region]
    step(f"Delete TableTap from AWS ({region})")
    print("    This permanently deletes the app, the database (no final snapshot) and the image registry.")
    if input(f"    Type {APP} to confirm: ").strip() != APP:
        fail("Not deleted.")
    svc = find_apprunner(R)
    if svc:
        aws("apprunner", "delete-service", "--service-arn", svc, *R, check=False)
        info("App Runner service deleting…")
    aws("rds", "delete-db-instance", "--db-instance-identifier", f"{APP}-db", "--skip-final-snapshot",
        "--delete-automated-backups", *R, check=False)
    aws("ecr", "delete-repository", "--repository-name", APP, "--force", *R, check=False)
    for k in ("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD"):
        aws("ssm", "delete-parameter", "--name", f"/{APP}/{k}", *R, parse=False, check=False)
    info("Waiting for the database and app to finish deleting…")
    aws("rds", "wait", "db-instance-deleted", "--db-instance-identifier", f"{APP}-db", *R, parse=False, check=False)
    for _ in range(60):
        if not find_apprunner(R):
            break
        time.sleep(10)
    for c in aws("apprunner", "list-vpc-connectors", *R)["VpcConnectors"]:
        if c["VpcConnectorName"] == APP and c["Status"] == "ACTIVE":
            aws("apprunner", "delete-vpc-connector", "--vpc-connector-arn", c["VpcConnectorArn"], *R, check=False)
    time.sleep(30)
    for name in (f"{APP}-db", f"{APP}-app"):
        for sg in aws("ec2", "describe-security-groups", "--filters", f"Name=group-name,Values={name}", *R)["SecurityGroups"]:
            aws("ec2", "delete-security-group", "--group-id", sg["GroupId"], *R, parse=False, check=False)
    for role in (f"{APP}-github-deploy", f"{APP}-apprunner-ecr", f"{APP}-apprunner-instance"):
        for p in (aws("iam", "list-role-policies", "--role-name", role, check=False) or {}).get("PolicyNames", []):
            aws("iam", "delete-role-policy", "--role-name", role, "--policy-name", p, parse=False, check=False)
        for p in (aws("iam", "list-attached-role-policies", "--role-name", role, check=False) or {}).get("AttachedPolicies", []):
            aws("iam", "detach-role-policy", "--role-name", role, "--policy-arn", p["PolicyArn"], parse=False, check=False)
        aws("iam", "delete-role", "--role-name", role, parse=False, check=False)
    info("Done. (The GitHub OIDC provider and GitHub variables are left in place.)")


# ============================================================== main

def main():
    p = argparse.ArgumentParser(description="Deploy TableTap to Azure or AWS and wire up the GitHub pipeline.")
    p.add_argument("cloud", choices=["azure", "aws"])
    p.add_argument("--delete", action="store_true", help="remove everything this script created")
    p.add_argument("--repo", help="GitHub repo as owner/name (default: detected from git remote)")
    p.add_argument("--admin-email", help="email for the TableTap admin account")
    p.add_argument("--location", default="australiaeast", help="Azure region")
    p.add_argument("--resource-group", default="rg-tabletap", help="Azure resource group")
    p.add_argument("--region", default="ap-southeast-2", help="AWS region")
    p.add_argument("--skip-build", action="store_true", help="don't run the pipeline, reuse the latest image")
    args = p.parse_args()

    state = load_state()
    if state.get("target") and state["target"] != args.cloud and not args.delete:
        if not ask_yes(f"This folder was last deployed to {state['target']}. Set up {args.cloud} as well?"):
            sys.exit(0)
    if args.cloud == "azure":
        azure_delete(args, state) if args.delete else azure_deploy(args, state)
    else:
        aws_delete(args, state) if args.delete else aws_deploy(args, state)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nStopped. Re-run any time: it picks up where it left off.")
        sys.exit(130)
    except GitHubError as e:
        fail(str(e))
