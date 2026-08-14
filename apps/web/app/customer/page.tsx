"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";
import { Device, DeviceTwin, Earnings, Site } from "@/lib/api-types";

export default function CustomerPage() {
  const { api, ready, authenticated } = useAuth();
  const [sites, setSites] = useState<Site[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [devices, setDevices] = useState<Device[]>([]);
  const [live, setLive] = useState<DeviceTwin[]>([]);
  const [earnings, setEarnings] = useState<Earnings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !authenticated) return;
    Promise.all([
      api<Site[]>("/api/v1/sites"),
      api<Earnings>("/api/v1/customers/me/earnings"),
    ]).then(([siteRows, earningSummary]) => {
      setSites(siteRows);
      setSelectedId((current) => current || siteRows[0]?.id || "");
      setEarnings(earningSummary);
    }).catch((cause: Error) => setError(cause.message))
      .finally(() => setLoading(false));
  }, [api, authenticated, ready]);

  useEffect(() => {
    if (!selectedId) return;
    Promise.all([
      api<Device[]>(`/api/v1/sites/${selectedId}/devices`),
      api<DeviceTwin[]>(`/api/v1/sites/${selectedId}/live`),
    ]).then(([deviceRows, liveRows]) => {
      setDevices(deviceRows);
      setLive(liveRows);
    }).catch((cause: Error) => setError(cause.message));
  }, [api, selectedId]);

  const selected = sites.find((site) => site.id === selectedId);

  return (
    <AppShell
      role="CUSTOMER"
      title="My energy"
      description="See what your flexible devices are doing and how participation is rewarded."
    >
      {loading && <p className="status" aria-live="polite">Loading your energy portfolio...</p>}
      {error && <p className="notice error-message" role="alert">{error}</p>}
      {!loading && sites.length === 0 && (
        <section className="empty-state">
          <h2>No site is connected yet</h2>
          <p>Ask an administrator to add your organization membership, then register a site.</p>
        </section>
      )}
      {sites.length > 0 && (
        <>
          <label className="field compact-field">
            <span>Selected site</span>
            <select value={selectedId} onChange={(event) => {
              setDevices([]);
              setLive([]);
              setError(null);
              setSelectedId(event.target.value);
            }}>
              {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
            </select>
          </label>

          <section className="metric-grid" aria-label="Energy summary">
            <Metric label="Connected devices" value={String(devices.length)} />
            <Metric label="Online now" value={String(live.filter((item) => item.online).length)} />
            <Metric
              label="Current power"
              value={`${live.reduce((sum, item) => sum + Number(item.activePowerKw), 0).toFixed(1)} kW`}
            />
            <Metric
              label="Lifetime earnings"
              value={`${Number(earnings?.totalAmount ?? 0).toFixed(2)} ${earnings?.currency ?? "VWC"}`}
            />
          </section>

          <div className="content-grid">
            <section className="panel">
              <div className="panel-heading">
                <div><p className="eyebrow">SITE</p><h2>{selected?.name}</h2></div>
                <span className="badge">{selected?.status}</span>
              </div>
              <dl className="detail-list">
                <div><dt>Region</dt><dd>{selected?.region}, {selected?.country}</dd></div>
                <div><dt>VPP participation</dt><dd>{selected?.vppOptIn ? "Enabled" : "Paused"}</dd></div>
                <div><dt>Battery reserve</dt><dd>{selected?.minimumBatteryReservePercent}%</dd></div>
              </dl>
            </section>

            <section className="panel">
              <div className="panel-heading"><div><p className="eyebrow">DEVICES</p><h2>Asset status</h2></div></div>
              {devices.length === 0 ? <p className="text-muted">No device is registered at this site.</p> : (
                <div className="table-wrap"><table>
                  <thead><tr><th>Device</th><th>Type</th><th>Power</th><th>Status</th></tr></thead>
                  <tbody>{devices.map((device) => {
                    const twin = live.find((item) => item.deviceId === device.id);
                    return <tr key={device.id}>
                      <td><strong>{device.externalDeviceId}</strong><small>{device.manufacturer} {device.model}</small></td>
                      <td>{device.type}</td>
                      <td>{twin ? `${Number(twin.activePowerKw).toFixed(1)} kW` : "No signal"}</td>
                      <td><span className="badge">{twin?.online ? "ONLINE" : device.status}</span></td>
                    </tr>;
                  })}</tbody>
                </table></div>
              )}
            </section>
          </div>
        </>
      )}
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="metric"><span>{label}</span><strong>{value}</strong></div>;
}
