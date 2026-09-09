import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth-context";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "NEXLIB — Library Management",
  description:
    "Library management for the Government Degree Colleges of Khyber Pakhtunkhwa",
};

// Applied before first paint so a dark-mode user never sees a white flash, and
// so every page - not just the dashboard shell - is in the right theme from the
// start. The dashboard's toggle writes the same key.
const THEME_BOOTSTRAP = `
try {
  var t = localStorage.getItem("web-theme");
  if (t !== "light") document.documentElement.classList.add("dark");
} catch (e) {
  document.documentElement.classList.add("dark");
}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_BOOTSTRAP }} />
      </head>
      <body className="min-h-full flex flex-col bg-app text-body">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
