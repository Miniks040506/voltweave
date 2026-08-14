export type Site = {
  id: string;
  organizationId: string;
  name: string;
  timezone: string;
  region: string;
  country: string;
  status: string;
  vppOptIn: boolean;
  minimumBatteryReservePercent: number;
};

export type Device = {
  id: string;
  siteId: string;
  externalDeviceId: string;
  type: string;
  manufacturer: string;
  model: string;
  ratedPowerKw: number;
  status: string;
};

export type DeviceTwin = {
  deviceId: string;
  deviceType: string;
  observedAt: string;
  activePowerKw: number;
  socPercent: number | null;
  online: boolean;
  quality: string;
};

export type Earnings = {
  totalAmount: number;
  currency: string;
  entries: {
    id: string;
    siteId: string;
    entryType: string;
    energyKwh: number;
    amount: number;
    createdAt: string;
  }[];
};

export type Vpp = {
  id: string;
  organizationId: string;
  name: string;
  region: string;
  status: string;
  memberships: { id: string; siteId: string; status: string }[];
  automationPolicy: {
    enabled: boolean;
    approvalMode: string;
    reserveMarginPercent: number;
    maxDispatchPowerKw: number;
    maxDispatchDurationMinutes: number;
  };
};

export type Forecast = {
  id: string;
  version: number;
  modelName: string;
  modelVersion: string;
  targetStart: string;
  targetEnd: string;
  validUntil: string;
  points: { forecastAt: string; baselineGridImportKw: number; solarGenerationKw: number }[];
};

export type Flexibility = {
  id: string;
  version: number;
  generatedAt: string;
  validUntil: string;
  upwardFlexibilityKw: number;
  availableEnergyKwh: number;
  candidates: {
    deviceId: string;
    deviceType: string;
    upwardFlexibilityKw: number;
    limitingReason: string | null;
  }[];
};

export type Optimization = {
  id: string;
  version: number;
  targetPowerKw: number;
  requiredPowerKw: number;
  plannedPowerKw: number;
  feasible: boolean;
  candidates: { deviceId: string; allocatedPowerKw: number; eligible: boolean }[];
};

export type Dispatch = {
  id: string;
  vppId: string;
  optimizationPreviewId: string;
  targetPowerKw: number;
  requiredPowerKw: number;
  plannedPowerKw: number;
  scheduledStartAt: string;
  scheduledEndAt: string;
  status: string;
  allocations: { siteId: string; deviceId: string; allocatedPowerKw: number }[];
};

export type AutomationCandidate = {
  policyId: string;
  policyVersion: number;
  evaluatedAt: string;
  dispatch: Dispatch;
};

export type Organization = {
  id: string;
  type: string;
  legalName: string;
  displayName: string;
  tenantCode: string;
  status: string;
  country: string;
  timezone: string;
};

export type AuditEntry = {
  id: string;
  actorType: string;
  actorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  occurredAt: string;
  correlationId: string;
};
