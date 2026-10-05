import { useState, useEffect } from 'react';
import './index.css';

const API_BASE = 'http://localhost:8080/api';

function App() {
  const [inventory, setInventory] = useState({
    total: 100, available: 100, reserved: 0, sold: 0,
  });

  const [stats, setStats] = useState({
    requests: 0, success: 0, rejected: 0, overselling: 0,
  });

  const [status, setStatus] = useState({
    reservation: 'IDLE', payment: 'IDLE', order: 'IDLE', system: 'HEALTHY'
  });

  // Poll backend for live stats every 2 seconds
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await fetch(`${API_BASE}/inventory/stats`);
        if (res.ok) {
          const data = await res.json();
          setInventory({
            total: data.total,
            available: data.available,
            reserved: data.reserved,
            sold: data.sold
          });
          setStats(prev => ({
            ...prev,
            success: data.success,
            overselling: data.overselling
          }));
        }
      } catch (err) {
        setStatus(prev => ({ ...prev, system: 'BACKEND_OFFLINE' }));
      }
    };

    fetchStats();
    const intervalId = setInterval(fetchStats, 2000);
    return () => clearInterval(intervalId);
  }, []);

  const triggerSimulation = () => {
    alert("Simulation triggered! (In a full setup, this would call k6 or the Python script)");
  };

  const injectFailure = async (type: string) => {
    try {
      const res = await fetch(`${API_BASE}/admin/simulate-failure`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ failure_type: type })
      });
      
      if (res.ok) {
        setStatus(prev => ({ ...prev, system: `WARNING: ${type} INJECTED` }));
      }
    } catch (err) {
      alert("Failed to reach backend to inject failure.");
    }
  };

  return (
    <div className="dashboard-container">
      <header className="header">
        <h1>SALESTORM FLASH SALE</h1>
        <p>Live Backend Connection Demo</p>
      </header>

      <div className="grid">
        <div className="card">
          <h2>Inventory Status</h2>
          <div className="metric"><span>Product:</span> <span className="metric-value">Gaming GPU RTX 5090</span></div>
          <div className="metric"><span>Total Inventory:</span> <span className="metric-value">{inventory.total}</span></div>
          <br/>
          <div className="metric"><span>Available:</span> <span className="metric-value value-safe">{inventory.available}</span></div>
          <div className="metric"><span>Reserved:</span> <span className="metric-value value-warn">{inventory.reserved}</span></div>
          <div className="metric"><span>Sold:</span> <span className="metric-value">{inventory.sold}</span></div>
        </div>

        <div className="card">
          <h2>Traffic Stats</h2>
          <div className="metric"><span>Purchase Requests:</span> <span className="metric-value">{stats.requests}</span></div>
          <div className="metric"><span>Successful Purchases:</span> <span className="metric-value value-safe">{stats.success}</span></div>
          <div className="metric"><span>Rejected (Sold Out):</span> <span className="metric-value value-danger">{stats.rejected}</span></div>
          <br/>
          <div className="metric">
            <span>Overselling:</span> 
            <span className={`metric-value ${stats.overselling > 0 ? 'value-danger' : 'value-safe'}`}>
              {stats.overselling}
            </span>
          </div>
        </div>

        <div className="card">
          <h2>Lifecycle State</h2>
          <div className="metric"><span>Reservation:</span> <span className="metric-value">{status.reservation}</span></div>
          <div className="metric"><span>Payment:</span> <span className="metric-value">{status.payment}</span></div>
          <div className="metric"><span>Order:</span> <span className="metric-value">{status.order}</span></div>
          <br/>
          <div className="metric"><span>System Health:</span> <span className={`metric-value ${status.system === 'HEALTHY' ? 'value-safe' : 'value-danger'}`}>{status.system}</span></div>
        </div>

        <div className="card" style={{ gridColumn: '1 / -1' }}>
          <h2>Jury Controls & Failure Injection</h2>
          <div className="controls-grid">
            <button className="btn-primary" onClick={triggerSimulation}>🚀 Run 10,000 Concurrent Purchases</button>
            <button className="btn-danger" onClick={() => injectFailure('PAYMENT_FAILURE')}>Simulate Payment Failure</button>
            <button className="btn-danger" onClick={() => injectFailure('PAYMENT_TIMEOUT')}>Simulate Payment Timeout</button>
            <button className="btn-danger" onClick={() => injectFailure('ORDER_SERVICE_CRASH')}>Simulate Order Crash</button>
            <button className="btn-danger" onClick={() => injectFailure('DATABASE_DROP')}>Simulate DB Drop</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
