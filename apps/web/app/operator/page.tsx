"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";
import { Dispatch, Flexibility, Forecast, Vpp } from "@/lib/api-types";

export default function OperatorPage() {
  const { api, ready, authenticated } = useAuth();
  const [vpps, setVpps] = useState<Vpp[]>([]);
  const [vppId, setVppId] = useState("");
  const [forecast, setForecast] = useState<Forecast | null>(null);
  const [flexibility, setFlexibility] = useState<Flexibility | null>(null);
  const [dispatches, setDispatches] = useState<Dispatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !authenticated) return;
    api<Vpp[]>("/api/v1/vpps").then((rows) => {
      setVpps(rows);
      setVppId((current) => current || rows[0]?.id || "");
    }).catch((cause: Error) => setError(cause.message))
      .finally(() => setLoading(false));
  }, [api, authenticated, ready]);

  useEffect(() => {
    if (!vppId) return;
    Promise.all([
      api<Dispatch[]>(`/api/v1/dispatches?vppId=${vppId}`),
      api<Forecast>(`/api/v1/vpps/${vppId}/forecast`).catch(() => null),
      api<Flexibility>(`/api/v1/vpps/${vppId}/flexibility`).catch(() => null),
    ]).then(([dispatchRows, latestForecast, latestFlexibility]) => {
      setDispatches(dispatchRows);
      setForecast(latestForecast);
      setFlexibility(latestFlexibility);
    }).catch((cause: Error) => setError(cause.message));
  }, [api, vppId]);

  const selected = vpps.find((vpp) => vpp.id === vppId);

  return (
    <AppShell
      role="VPP_OPERATOR"
      title="VPP operations"
      description="Prepare a dispatch from a versioned forecast and a current flexibility snapshot."
    >
      {loading && <p className="status">Loading VPP portfolio...</p>}
      {error && <p className="notice error-message" role="alert">{error}</p>}
      {!loading && vpps.length === 0 && (
        <section className="empty-state"><h2>No VPP is assigned</h2><p>Create a VPP and add an active operator membership first.</p></section>
      )}
      {vpps.length > 0 && <>
        <label className="field compact-field">
          <span>Selected VPP</span>
          <select value={vppId} onChange={(event) => {
            setForecast(null);
            setFlexibility(null);
            setDispatches([]);
            setError(null);
            setVppId(event.target.value);
          }}>
            {vpps.map((vpp) => <option key={vpp.id} value={vpp.id}>{vpp.name}</option>)}
          </select>
        </label>

        <section className="metric-grid">
          <Metric label="Participating sites" value={String(selected?.memberships.filter((item) => item.status === "ACTIVE").length ?? 0)} />
          <Metric label="Upward flexibility" value={`${Number(flexibility?.upwardFlexibilityKw ?? 0).toFixed(1)} kW`} />
          <Metric label="Available energy" value={`${Number(flexibility?.availableEnergyKwh ?? 0).toFixed(1)} kWh`} />
          <Metric label="Dispatches" value={String(dispatches.length)} />
        </section>

        <div className="content-grid equal-columns">
          <section className="panel">
            <div className="panel-heading"><div><p className="eyebrow">INTELLIGENCE</p><h2>Latest operating inputs</h2></div></div>
            <dl className="detail-list">
              <div><dt>Forecast</dt><dd>{forecast ? `v${forecast.version} · ${forecast.modelName}` : "Not generated"}</dd></div>
              <div><dt>Forecast valid until</dt><dd>{formatTime(forecast?.validUntil)}</dd></div>
              <div><dt>Flexibility</dt><dd>{flexibility ? `v${flexibility.version}` : "Not generated"}</dd></div>
              <div><dt>Eligible devices</dt><dd>{flexibility?.candidates.filter((item) => item.eligible).length ?? 0}</dd></div>
            </dl>
          </section>

          <section className="panel">
            <div className="panel-heading"><div><p className="eyebrow">HISTORY</p><h2>Recent dispatches</h2></div></div>
            {dispatches.length === 0 ? <p className="text-muted">No dispatch has been prepared for this VPP.</p> : (
              <div className="table-wrap"><table>
                <thead><tr><th>Start</th><th>Target</th><th>Planned</th><th>Status</th></tr></thead>
                <tbody>{dispatches.map((dispatch) => <tr key={dispatch.id}>
                  <td>{formatTime(dispatch.scheduledStartAt)}</td>
                  <td>{Number(dispatch.targetPowerKw).toFixed(1)} kW</td>
                  <td>{Number(dispatch.plannedPowerKw).toFixed(1)} kW</td>
                  <td><span className="badge">{dispatch.status}</span></td>
                </tr>)}</tbody>
              </table></div>
            )}
          </section>
        </div>
      </>}
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="metric"><span>{label}</span><strong>{value}</strong></div>;
}

function formatTime(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "Unavailable";
}
