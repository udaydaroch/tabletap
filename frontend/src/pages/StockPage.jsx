import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import StockPanel from '../components/StockPanel.jsx';

/** Standalone stock screen for chefs (owners also see it as a tab on the restaurant page). */
export default function StockPage() {
  const { id } = useParams();
  const { me } = useAuth();
  return (
    <>
      <div className="page-head">
        <h2>Stock</h2>
        <Link className="btn" to={`/restaurants/${id}/kitchen`}>Kitchen</Link>
      </div>
      <StockPanel rid={id} canManage={me.role === 'OWNER' || me.role === 'ADMIN'} />
    </>
  );
}
