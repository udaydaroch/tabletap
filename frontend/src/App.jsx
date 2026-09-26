import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Home from './pages/Home.jsx';
import RestaurantPage from './pages/RestaurantPage.jsx';
import TakeOrder from './pages/TakeOrder.jsx';
import Kitchen from './pages/Kitchen.jsx';
import Today from './pages/Today.jsx';
import OrgTree from './pages/OrgTree.jsx';
import Admin from './pages/Admin.jsx';

export default function App() {
  const { me, loading, unreachable } = useAuth();
  if (loading) {
    return (
      <div className="center-screen">
        {unreachable ? "Can't reach the TableTap server — retrying…" : 'Loading…'}
      </div>
    );
  }

  if (!me) {
    return (
      <Routes>
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }

  const manager = me.role === 'OWNER' || me.role === 'ADMIN';
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/restaurants/:id" element={manager ? <RestaurantPage /> : <Navigate to="/" />} />
        {me.role !== 'CHEF' && <Route path="/restaurants/:id/order" element={<TakeOrder />} />}
        <Route path="/restaurants/:id/kitchen" element={<Kitchen />} />
        <Route path="/restaurants/:id/today" element={<Today />} />
        {manager && <Route path="/tree" element={<OrgTree />} />}
        {me.role === 'ADMIN' && <Route path="/admin" element={<Admin />} />}
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Layout>
  );
}
