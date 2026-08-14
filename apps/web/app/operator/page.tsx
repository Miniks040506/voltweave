"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";
import { Dispatch, Flexibility, Forecast, Optimization, Vpp } from "@/lib/api-types";

export default function OperatorPage() {
  const { api, ready, authenticated } = useAuth();
  const [vpps, setVpps] = useState<Vpp[]>([]);
  const [vppId, setVppId] = useState("");
  const [forecast, setForecast] = useState<Forecast | null>(null);
  const [flexibility, setFlexibility] = useState<Flexibility | null>(null);
  const [dispatches, setDispatches] = useState<Dispatch[]>([]);
  const [targetStart, setTargetStart] = useState("");
  const [duration, setDuration] = useState(30);
  const [targetPower, setTargetPower] = useState(10);
  const [reserveMargin, setReserveMargin] = useState(10);
  const [preview, setPreview] = useState<Optimization | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState("");
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

  async function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setWorking(true);
    setError(null);
    setMessage("");
    setPreview(null);
    try {
      const start = new Date(targetStart).toISOString();
      const nextForecast = await api<Forecast>(`/api/v1/vpps/${vppId}/forecast`, {
        method: "POST",
        body: JSON.stringify({ horizon: "HOUR_1", targetStart: start }),
      });
      const nextFlexibility = await api<Flexibility>(`/api/v1/vpps/${vppId}/flexibility`, {
        method: "POST",
        body: JSON.stringify({ dispatchDurationMinutes: duration }),
      });
      const nextPreview = await api<Optimization>(`/api/v1/vpps/${vppId}/optimization-preview`, {
        method: "POST",
        body: JSON.stringify({ targetPowerKw: targetPower, reserveMarginPercent: reserveMargin }),
      });
      setForecast(nextForecast);
      setFlexibility(nextFlexibility);
      setPreview(nextPreview);
      setIdempotencyKey(crypto.randomUUID());
      setMessage(nextPreview.feasible ? "Preview is ready for review." : "The requested target is not feasible.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not prepare dispatch inputs.");
    } finally {
      setWorking(false);
    }
  }

  async function confirmDispatch() {
    if (!preview) return;
    setWorking(true);
    setError(null);
    try {
      const created = await api<Dispatch>("/api/v1/dispatches", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({
          vppId,
          optimizationPreviewId: preview.id,
          type: "REDUCE_DEMAND",
          scheduledStartAt: new Date(targetStart).toISOString(),
          durationMinutes: duration,
        }),
      });
      setDispatches((current) => [created, ...current]);
      setPreview(null);
      setMessage(`Dispatch ${created.id.slice(0, 8)} was scheduled.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create dispatch.");
    } finally {
      setWorking(false);
    }
  }

  async function prepareCommands(dispatch: Dispatch) {
    setWorking(true);
    setError(null);
    try {
      const commands = await api<unknown[]>(`/api/v1/dispatches/${dispatch.id}/commands`, { method: "POST" });
      const updated = await api<Dispatch>(`/api/v1/dispatches/${dispatch.id}`);
      setDispatches((current) => current.map((item) => item.id === updated.id ? updated : item));
      setMessage(`${commands.length} device command${commands.length === 1 ? "" : "s"} prepared.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not prepare device commands.");
    } finally {
      setWorking(false);
    }
  }

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

        <section className="panel workflow-panel">
          <div className="panel-heading"><div><p className="eyebrow">MANUAL CONTROL</p><h2>Prepare a dispatch</h2></div></div>
          <form className="dispatch-form" onSubmit={prepare}>
            <label className="field"><span>Start time</span><input type="datetime-local" step="900" value={targetStart} onChange={(event) => setTargetStart(event.target.value)} required /></label>
            <label className="field"><span>Duration (minutes)</span><input type="number" min="15" max="1440" step="15" value={duration} onChange={(event) => setDuration(Number(event.target.value))} required /></label>
            <label className="field"><span>Target power (kW)</span><input type="number" min="0.1" step="0.1" value={targetPower} onChange={(event) => setTargetPower(Number(event.target.value))} required /></label>
            <label className="field"><span>Reserve margin (%)</span><input type="number" min="0" max="100" value={reserveMargin} onChange={(event) => setReserveMargin(Number(event.target.value))} required /></label>
            <button className="primary-button" disabled={working}>Prepare inputs</button>
          </form>
          {preview && <div className="preview-strip" aria-live="polite">
            <div><span>Required</span><strong>{Number(preview.requiredPowerKw).toFixed(1)} kW</strong></div>
            <div><span>Planned</span><strong>{Number(preview.plannedPowerKw).toFixed(1)} kW</strong></div>
            <div><span>Devices</span><strong>{preview.candidates.filter((item) => item.eligible).length}</strong></div>
            <button className="primary-button" disabled={working || !preview.feasible} onClick={() => void confirmDispatch()}>Confirm dispatch</button>
          </div>}
          {message && <p className="status" aria-live="polite">{message}</p>}
        </section>

        <div className="content-grid equal-columns">
          <section className="panel">
            <div className="panel-heading"><div><p className="eyebrow">INTELLIGENCE</p><h2>Latest operating inputs</h2></div></div>
            <dl className="detail-list">
              <div><dt>Forecast</dt><dd>{forecast ? `v${forecast.version} · ${forecast.modelName}` : "Not generated"}</dd></div>
              <div><dt>Forecast valid until</dt><dd>{formatTime(forecast?.validUntil)}</dd></div>
              <div><dt>Flexibility</dt><dd>{flexibility ? `v${flexibility.version}` : "Not generated"}</dd></div>
              <div><dt>Eligible devices</dt><dd>{flexibility?.candidates.filter((item) => item.limitingReason === null).length ?? 0}</dd></div>
            </dl>
          </section>

          <section className="panel">
            <div className="panel-heading"><div><p className="eyebrow">HISTORY</p><h2>Recent dispatches</h2></div></div>
            {dispatches.length === 0 ? <p className="text-muted">No dispatch has been prepared for this VPP.</p> : (
              <div className="table-wrap"><table>
                <thead><tr><th>Start</th><th>Target</th><th>Planned</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>{dispatches.map((dispatch) => <tr key={dispatch.id}>
                  <td>{formatTime(dispatch.scheduledStartAt)}</td>
                  <td>{Number(dispatch.targetPowerKw).toFixed(1)} kW</td>
                  <td>{Number(dispatch.plannedPowerKw).toFixed(1)} kW</td>
                  <td><span className="badge">{dispatch.status}</span></td>
                  <td>{dispatch.status === "SCHEDULED" ? <button className="text-button" disabled={working} onClick={() => void prepareCommands(dispatch)}>Prepare commands</button> : "Prepared"}</td>
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
