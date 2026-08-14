"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { AppRole, keycloak, roles } from "@/lib/keycloak";

type AuthContextValue = {
  ready: boolean;
  authenticated: boolean;
  name: string;
  roles: AppRole[];
  error: string | null;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  api: <T>(path: string, init?: RequestInit) => Promise<T>;
  download: (path: string, filename: string) => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [currentRoles, setRoles] = useState<AppRole[]>([]);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
    }).then((loggedIn) => {
      setAuthenticated(loggedIn);
      setRoles(roles());
      setName(keycloak.tokenParsed?.name ?? keycloak.tokenParsed?.preferred_username ?? "User");
    }).catch(() => setError("Could not connect to the identity service."))
      .finally(() => setReady(true));
  }, []);

  const api = useCallback(async <T,>(path: string, init?: RequestInit): Promise<T> => {
    if (!keycloak.authenticated) throw new Error("Your session has expired.");
    await keycloak.updateToken(30);
    const response = await fetch(`/backend${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
        Authorization: `Bearer ${keycloak.token}`,
      },
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      throw new Error(problem?.detail ?? problem?.title ?? `Request failed (${response.status})`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }, []);

  const download = useCallback(async (path: string, filename: string) => {
    if (!keycloak.authenticated) throw new Error("Your session has expired.");
    await keycloak.updateToken(30);
    const response = await fetch(`/backend${path}`, {
      headers: { Authorization: `Bearer ${keycloak.token}` },
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      throw new Error(problem?.detail ?? problem?.title ?? `Download failed (${response.status})`);
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }, []);

  const value: AuthContextValue = {
    ready, authenticated, name, roles: currentRoles, error, api, download,
    login: () => keycloak.login({ redirectUri: window.location.origin }),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
