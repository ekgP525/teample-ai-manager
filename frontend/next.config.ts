import type { NextConfig } from "next";
import { PHASE_PRODUCTION_BUILD } from "next/constants";

const nextConfig: NextConfig = {
  /* config options here */
};

export default function config(phase: string) {
  if (phase === PHASE_PRODUCTION_BUILD) {
    for (const name of ["NEXT_PUBLIC_API_URL", "NEXT_PUBLIC_SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_ANON_KEY"]) {
      const value = process.env[name];
      if (!value?.trim() || value.includes("<")) {
        throw new Error(`Missing build setting: ${name}. Configure it before running npm run build.`);
      }
    }
    for (const name of ["NEXT_PUBLIC_API_URL", "NEXT_PUBLIC_SUPABASE_URL"]) {
      const url = new URL(process.env[name]!);
      if (url.protocol !== "https:" && !(url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname))) {
        throw new Error(`${name} must use HTTPS (HTTP is only allowed for localhost).`);
      }
    }
  }
  return nextConfig;
}
