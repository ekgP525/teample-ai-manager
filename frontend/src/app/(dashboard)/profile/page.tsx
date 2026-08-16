"use client";

import { useEffect, useState } from "react";
import { SignOutButton } from "@/components/sign-out-button";
import { supabase } from "@/lib/supabase";

interface ProfileData {
  name: string;
  email: string;
  provider: string;
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let isMounted = true;

    void supabase.auth
      .getUser()
      .then(({ data, error: userError }) => {
        if (userError) throw userError;
        if (!isMounted || !data.user) return;

        const metadata = data.user.user_metadata;
        setProfile({
          name:
            metadata.name ||
            metadata.full_name ||
            data.user.email?.split("@")[0] ||
            "사용자",
          email: data.user.email || "이메일 정보 없음",
          provider: formatProvider(data.user.app_metadata.provider),
        });
      })
      .catch(() => {
        if (isMounted) {
          setError("프로필 정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8">
          <h1 className="text-2xl font-bold">프로필</h1>
          <p className="mt-1 text-sm text-zinc-500">
            현재 로그인한 계정 정보를 확인합니다.
          </p>
        </div>

        {isLoading ? (
          <div
            aria-live="polite"
            className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
          >
            <p className="text-zinc-500">프로필을 불러오는 중...</p>
          </div>
        ) : error || !profile ? (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
          >
            <p className="text-sm text-red-600 dark:text-red-400">
              {error || "프로필 정보가 없습니다."}
            </p>
          </div>
        ) : (
          <section className="overflow-hidden rounded-lg border border-zinc-200 dark:border-zinc-700">
            <div className="flex items-center gap-4 border-b border-zinc-200 p-5 dark:border-zinc-700">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-zinc-100 text-lg font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200">
                {profile.name.slice(0, 1).toUpperCase()}
              </div>
              <div className="min-w-0">
                <h2 className="truncate font-semibold">{profile.name}</h2>
                <p className="truncate text-sm text-zinc-500">
                  {profile.email}
                </p>
              </div>
            </div>
            <dl className="divide-y divide-zinc-100 px-5 dark:divide-zinc-800">
              <ProfileRow label="이름" value={profile.name} />
              <ProfileRow label="이메일" value={profile.email} />
              <ProfileRow label="로그인 방식" value={profile.provider} />
            </dl>
            <div className="border-t border-zinc-200 p-5 dark:border-zinc-700">
              <SignOutButton className="rounded-lg border border-zinc-300 px-4 py-2 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800" />
            </div>
          </section>
        )}
      </div>
    </main>
  );
}

function ProfileRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1 py-4 sm:grid-cols-[8rem_1fr] sm:gap-4">
      <dt className="text-sm text-zinc-500">{label}</dt>
      <dd className="break-words text-sm font-medium">{value}</dd>
    </div>
  );
}

function formatProvider(provider?: string) {
  if (provider === "google") return "Google";
  if (provider === "kakao") return "카카오";
  if (provider === "email") return "이메일";
  return provider || "알 수 없음";
}
