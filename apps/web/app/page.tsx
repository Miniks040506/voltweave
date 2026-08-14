"use client";

import Link from "next/link";
import { useAuth } from "@/components/auth-provider";
import { homeFor } from "@/lib/keycloak";

export default function Home() {
  const auth = useAuth();

  return (
    <main className="flex min-h-screen items-center px-6 py-16">
      <section className="mx-auto w-full max-w-5xl">
        <p className="eyebrow">VOLTWEAVE / V1</p>
        <h1 className="mt-8 max-w-4xl text-5xl font-semibold tracking-[-0.045em] sm:text-7xl">
          Flexible energy, coordinated with confidence.
        </h1>
        <p className="mt-8 max-w-2xl text-lg leading-8 text-muted">
          Monitor household assets, prepare VPP dispatches and verify settlement
          from one operational workspace.
        </p>

        <div className="mt-12 min-h-20" aria-live="polite">
          {!auth.ready && <p className="status">Checking your session...</p>}
          {auth.error && <p className="error-message">{auth.error}</p>}
          {auth.ready && !auth.authenticated && (
            <button className="primary-button" onClick={() => void auth.login()}>
              Sign in to VoltWeave
            </button>
          )}
          {auth.ready && auth.authenticated && (
            <div className="flex flex-wrap items-center gap-4">
              <Link className="primary-button" href={homeFor(auth.roles)}>
                Open workspace
              </Link>
              <span className="text-sm text-muted">Signed in as {auth.name}</span>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
