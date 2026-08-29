"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export default function RootPage() {
  const { user, profile, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading) {
      if (user) {
        if (profile?.institutionId) {
          router.push("/dashboard");
        } else {
          router.push("/onboard");
        }
      } else {
        router.push("/login");
      }
    }
  }, [user, profile, loading, router]);

  return (
    <div className="flex h-screen w-full items-center justify-center bg-[#050B14]">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" />
    </div>
  );
}
