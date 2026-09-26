/**
 * Command pattern for the floor-plan designer.
 *
 * Every change is wrapped in a command object that knows how to apply itself and how to undo itself.
 * The history keeps two stacks: commands that were done (for undo) and commands that were undone (for redo).
 * A whole drag is recorded as ONE command, so undo puts a table back where the drag started.
 */
export class EditCommand {
  constructor(label, before, after) {
    this.label = label;
    this.before = before; // floor plan before the edit (immutable snapshot)
    this.after = after;   // floor plan after the edit
  }

  execute(apply) { apply(this.after); }

  undo(apply) { apply(this.before); }
}

export class CommandHistory {
  constructor(limit = 100) {
    this.limit = limit;
    this.done = [];
    this.undone = [];
  }

  /** Apply a command and remember it. */
  run(command, apply) {
    command.execute(apply);
    this.record(command);
  }

  /** Remember a command that has already been applied (e.g. a finished drag). */
  record(command) {
    if (command.before === command.after) return;
    this.done.push(command);
    if (this.done.length > this.limit) this.done.shift();
    this.undone = []; // a new edit makes the redo branch invalid
  }

  undo(apply) {
    const command = this.done.pop();
    if (!command) return null;
    command.undo(apply);
    this.undone.push(command);
    return command;
  }

  redo(apply) {
    const command = this.undone.pop();
    if (!command) return null;
    command.execute(apply);
    this.done.push(command);
    return command;
  }

  clear() {
    this.done = [];
    this.undone = [];
  }

  get undoLabel() { return this.done.at(-1)?.label ?? null; }

  get redoLabel() { return this.undone.at(-1)?.label ?? null; }
}
