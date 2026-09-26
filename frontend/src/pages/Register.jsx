import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

export default function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', restaurantName: '' });
  const [error, setError] = useState(null);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    try { await register(form); nav('/'); } catch (err) { setError(err.message); }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1 className="brand-lg">Start with TableTap</h1>
        <p className="muted">Owner account · pay only for what you use.</p>
        <label>Your name<input value={form.fullName} onChange={set('fullName')} required /></label>
        <label>Email<input type="email" value={form.email} onChange={set('email')} required /></label>
        <label>Password <span className="muted small">(10+ chars, letter + number)</span><input type="password" minLength={10} value={form.password} onChange={set('password')} required /></label>
        <label>First restaurant name <span className="muted">(optional)</span><input value={form.restaurantName} onChange={set('restaurantName')} /></label>
        {error && <div className="error">{error}</div>}
        <button className="btn primary block">Create account</button>
        <p className="muted small center">Already have one? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}
