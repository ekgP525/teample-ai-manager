import { AuthSessionGuard } from "@/components/auth-session-guard";
import { AppSidebar } from "@/components/app-sidebar";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthSessionGuard>
      <div className="min-h-screen md:flex">
        <AppSidebar />
        <div className="flex min-h-screen min-w-0 flex-1 flex-col pb-20 md:pb-0">
          {children}
        </div>
      </div>
    </AuthSessionGuard>
  );
}
