const ADMIN_SESSION_KEY = "teample.adminTestSession";

export type AdminTestSession = {
  adminId: string;
  password: string;
  authMode: "ADMIN_TEST";
};

function getStorage() {
  if (typeof window === "undefined") {
    return null;
  }
  return window.sessionStorage;
}

export function getAdminTestSession(): AdminTestSession | null {
  const storage = getStorage();
  if (!storage) {
    return null;
  }

  const raw = storage.getItem(ADMIN_SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as Partial<AdminTestSession>;
    if (session.authMode !== "ADMIN_TEST" || !session.adminId || !session.password) {
      clearAdminTestSession();
      return null;
    }
    return {
      adminId: session.adminId,
      password: session.password,
      authMode: "ADMIN_TEST",
    };
  } catch {
    clearAdminTestSession();
    return null;
  }
}

export function setAdminTestSession(adminId: string, password: string) {
  getStorage()?.setItem(
    ADMIN_SESSION_KEY,
    JSON.stringify({ adminId, password, authMode: "ADMIN_TEST" })
  );
}

export function clearAdminTestSession() {
  getStorage()?.removeItem(ADMIN_SESSION_KEY);
}

export function hasAdminTestSession() {
  return getAdminTestSession() !== null;
}
