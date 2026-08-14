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
