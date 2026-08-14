"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";
import { AuditEntry, Organization } from "@/lib/api-types";

export default function AdminPage() {
  const { api, ready, authenticated } = useAuth();
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationId, setOrganizationId] = useState("");
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [health, setHealth] = useState("UNKNOWN");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !authenticated) return;
    Promise.all([
      api<Organization[]>("/api/v1/organizations"),
      api<{ status: string }>("/actuator/health"),
    ]).then(([rows, gatewayHealth]) => {
      setOrganizations(rows);
      setOrganizationId((current) => current || rows[0]?.id || "");
      setHealth(gatewayHealth.status);
    }).catch((cause: Error) => setError(cause.message))
      .finally(() => setLoading(false));
  }, [api, authenticated, ready]);

  useEffect(() => {
    if (!organizationId) return;
    api<AuditEntry[]>(`/api/v1/audit?organizationId=${organizationId}&limit=50`)
      .then(setAudit)
      .catch((cause: Error) => setError(cause.message));
  }, [api, organizationId]);

  const selected = organizations.find((organization) => organization.id === organizationId);

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setCreating(true);
    setError(null);
    setMessage("");
    try {
      const created = await api<Organization>("/api/v1/organizations", {
        method: "POST",
        body: JSON.stringify({
          type: data.get("type"),
          legalName: data.get("legalName"),
          displayName: data.get("displayName"),
          tenantCode: data.get("tenantCode"),
          country: String(data.get("country")).toUpperCase(),
          timezone: data.get("timezone"),
        }),
      });
      setOrganizations((current) => [created, ...current]);
      setOrganizationId(created.id);
      setMessage(`${created.displayName} was created.`);
      form.reset();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create organization.");
    } finally {
      setCreating(false);
    }
  }

  return (
    <AppShell
      role="ADMIN"
      title="Platform control"
      description="Manage tenant organizations and review the actions recorded inside each security boundary."
    >
      {loading && <p className="status">Loading platform state...</p>}
      {error && <p className="notice error-message" role="alert">{error}</p>}

      <section className="panel workflow-panel">
        <div className="panel-heading"><div><p className="eyebrow">ONBOARDING</p><h2>Create an organization</h2></div></div>
        <form className="organization-form" onSubmit={createOrganization}>
          <label className="field"><span>Type</span><select name="type" defaultValue="COMMERCIAL_CUSTOMER"><option value="COMMERCIAL_CUSTOMER">Commercial customer</option><option value="VPP_OPERATOR">VPP operator</option><option value="PLATFORM_INTERNAL">Platform internal</option></select></label>
          <label className="field"><span>Legal name</span><input name="legalName" maxLength={160} required /></label>
          <label className="field"><span>Display name</span><input name="displayName" maxLength={120} required /></label>
          <label className="field"><span>Tenant code</span><input name="tenantCode" maxLength={63} placeholder="north-grid" required /></label>
          <label className="field"><span>Country code</span><input name="country" minLength={2} maxLength={2} defaultValue="VN" required /></label>
          <label className="field"><span>Timezone</span><input name="timezone" maxLength={64} defaultValue="Asia/Bangkok" required /></label>
          <button className="primary-button" disabled={creating}>{creating ? "Creating..." : "Create tenant"}</button>
        </form>
        {message && <p className="status" aria-live="polite">{message}</p>}
      </section>

      <section className="metric-grid">
        <Metric label="Organizations" value={String(organizations.length)} />
        <Metric label="Active tenants" value={String(organizations.filter((item) => item.status === "ACTIVE").length)} />
        <Metric label="Gateway health" value={health} />
        <Metric label="Recent audit events" value={String(audit.length)} />
      </section>

      {organizations.length === 0 && !loading ? (
        <section className="empty-state"><h2>No organization exists yet</h2><p>Create the first tenant to begin the customer or operator journey.</p></section>
      ) : (
        <>
          <label className="field compact-field">
            <span>Selected organization</span>
            <select value={organizationId} onChange={(event) => {
              setAudit([]);
              setError(null);
              setOrganizationId(event.target.value);
            }}>
              {organizations.map((organization) => <option key={organization.id} value={organization.id}>{organization.displayName}</option>)}
            </select>
          </label>

          <div className="content-grid">
            <section className="panel">
              <div className="panel-heading"><div><p className="eyebrow">TENANT</p><h2>{selected?.displayName}</h2></div><span className="badge">{selected?.status}</span></div>
              <dl className="detail-list">
                <div><dt>Legal name</dt><dd>{selected?.legalName}</dd></div>
                <div><dt>Tenant code</dt><dd>{selected?.tenantCode}</dd></div>
                <div><dt>Type</dt><dd>{selected?.type}</dd></div>
                <div><dt>Locale</dt><dd>{selected?.country} · {selected?.timezone}</dd></div>
              </dl>
            </section>

            <section className="panel">
              <div className="panel-heading"><div><p className="eyebrow">AUDIT</p><h2>Recent tenant activity</h2></div></div>
              {audit.length === 0 ? <p className="text-muted">No audit event has been recorded for this organization.</p> : (
                <div className="table-wrap"><table>
                  <thead><tr><th>Time</th><th>Action</th><th>Resource</th><th>Actor</th></tr></thead>
                  <tbody>{audit.map((entry) => <tr key={entry.id}>
                    <td>{formatTime(entry.occurredAt)}</td>
                    <td>{entry.action}</td>
                    <td>{entry.resourceType}<small>{entry.resourceId.slice(0, 8)}</small></td>
                    <td>{entry.actorType}<small>{entry.actorId}</small></td>
                  </tr>)}</tbody>
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

function formatTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
