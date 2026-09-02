"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

/**
 * The directorate view moved out of /dashboard.
 *
 * /dashboard is a single college's workspace and everything under it is scoped
 * to `profile.institutionId`; the directorate works across colleges and writes
 * nothing, so it now lives at /director with its own shell and access gate.
 * This redirect keeps existing bookmarks and sidebar links working.
 */
export default function DirectorateRedirect() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/director");
  }, [router]);

  return (
    <div className="flex flex-col items-center gap-4 py-24">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
      <p className="text-xs font-bold uppercase tracking-widest text-slate-500">
        Opening the directorate portal…
      </p>
      <Link href="/director" className="text-xs font-bold text-blue-400 hover:text-blue-300">
        Continue to /director
      </Link>
    </div>
  );
}
