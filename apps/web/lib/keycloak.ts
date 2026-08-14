import Keycloak from "keycloak-js";

export type AppRole = "CUSTOMER" | "VPP_OPERATOR" | "ADMIN";

const appRoles = new Set<AppRole>(["CUSTOMER", "VPP_OPERATOR", "ADMIN"]);

export const keycloak = new Keycloak({
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? "http://localhost:8180",
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? "voltweave",
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "voltweave-web",
});

export function roles(): AppRole[] {
  return (keycloak.tokenParsed?.realm_access?.roles ?? [])
    .filter((role): role is AppRole => appRoles.has(role as AppRole));
}

export function homeFor(currentRoles: AppRole[]): string {
  if (currentRoles.includes("ADMIN")) return "/admin";
  if (currentRoles.includes("VPP_OPERATOR")) return "/operator";
  return "/customer";
}
