import http from "k6/http";
import { check, fail, sleep } from "k6";

const baseUrl = __ENV.BASE_URL;
const keycloakUrl = __ENV.KEYCLOAK_URL;
const keycloakHost = __ENV.KEYCLOAK_HOST;
const siteId = __ENV.SITE_ID;
const deviceId = __ENV.DEVICE_ID;

export const options = {
  scenarios: {
    authorized_reads: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "30s",
      gracefulStop: "5s",
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:site_devices}": ["p(95)<300"],
    "http_req_duration{name:site_live}": ["p(95)<300"],
    "http_req_duration{name:device_twin}": ["p(95)<300"],
  },
};

export function setup() {
  const required = { BASE_URL: baseUrl, KEYCLOAK_URL: keycloakUrl,
    KEYCLOAK_HOST: keycloakHost, SITE_ID: siteId, DEVICE_ID: deviceId,
    USERNAME: __ENV.USERNAME, PASSWORD: __ENV.PASSWORD };
  for (const [name, value] of Object.entries(required)) {
    if (!value) fail(`${name} is required`);
  }

  const response = http.post(`${keycloakUrl}/protocol/openid-connect/token`, {
    client_id: __ENV.CLIENT_ID || "voltweave-e2e",
    grant_type: "password",
    username: __ENV.USERNAME,
    password: __ENV.PASSWORD,
  }, {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Host: keycloakHost,
    },
    tags: { name: "keycloak_token" },
  });

  if (!check(response, { "Keycloak issued a token": (value) => value.status === 200 })) {
    fail(`Keycloak login failed with ${response.status}: ${response.body}`);
  }
  return { accessToken: response.json("access_token") };
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.accessToken}` } };
  const devices = http.get(`${baseUrl}/api/v1/sites/${siteId}/devices`, {
    ...params, tags: { name: "site_devices" },
  });
  const live = http.get(`${baseUrl}/api/v1/sites/${siteId}/live`, {
    ...params, tags: { name: "site_live" },
  });
  const twin = http.get(`${baseUrl}/api/v1/devices/${deviceId}/twin`, {
    ...params, tags: { name: "device_twin" },
  });

  check(devices, { "site devices returned 200": (value) => value.status === 200 });
  check(live, { "site live returned 200": (value) => value.status === 200 });
  check(twin, { "device twin returned 200": (value) => value.status === 200 });
  sleep(0.2);
}
