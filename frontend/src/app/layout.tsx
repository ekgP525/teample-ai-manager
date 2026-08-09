import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ThemeToggle } from "@/components/theme-toggle";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "팀플 AI 매니저",
  description: "대학생 팀플을 AI가 자동화하는 웹 서비스",
};

const themeScript = `(function(){try{var s=localStorage.getItem("theme");var m=window.matchMedia("(prefers-color-scheme: dark)");var a=function(){var t=localStorage.getItem("theme")||(m.matches?"dark":"light");document.documentElement.setAttribute("data-theme",t)};a();m.addEventListener("change",a)}catch(e){}})()`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ko"
      data-theme="light"
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full flex flex-col bg-background text-foreground font-sans transition-colors duration-200">
        <ThemeToggle />
        {children}
      </body>
    </html>
  );
}
