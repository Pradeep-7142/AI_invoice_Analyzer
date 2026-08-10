import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi, type LoginPayload, type RegisterPayload } from "@/api/authApi";
import { registerSessionExpiredHandler } from "@/api/httpClient";
import { tokenStorage } from "@/api/tokenStorage";
import type { OrganizationSummary, OrgRole, UserSummary } from "@/types/auth";

interface AuthState {
  user: UserSummary | null;
  organization: OrganizationSummary | null;
  role: OrgRole | null;
}

interface AuthContextValue extends AuthState {
  isInitializing: boolean;
  isAuthenticated: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const EMPTY_STATE: AuthState = { user: null, organization: null, role: null };

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(EMPTY_STATE);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    registerSessionExpiredHandler(() => setState(EMPTY_STATE));
  }, []);

  useEffect(() => {
    async function restoreSession() {
      if (!tokenStorage.getAccessToken()) {
        setIsInitializing(false);
        return;
      }
      try {
        const me = await authApi.me();
        setState({ user: me.user, organization: me.organization, role: me.role });
      } catch {
        tokenStorage.clear();
        setState(EMPTY_STATE);
      } finally {
        setIsInitializing(false);
      }
    }
    restoreSession();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      isInitializing,
      isAuthenticated: state.user !== null,
      login: async (payload) => {
        const response = await authApi.login(payload);
        tokenStorage.setTokens(response.accessToken, response.refreshToken);
        setState({ user: response.user, organization: response.organization, role: response.role });
      },
      register: async (payload) => {
        const response = await authApi.register(payload);
        tokenStorage.setTokens(response.accessToken, response.refreshToken);
        setState({ user: response.user, organization: response.organization, role: response.role });
      },
      logout: async () => {
        const refreshToken = tokenStorage.getRefreshToken();
        tokenStorage.clear();
        setState(EMPTY_STATE);
        if (refreshToken) {
          await authApi.logout(refreshToken).catch(() => undefined);
        }
      },
    }),
    [state, isInitializing]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider.");
  }
  return ctx;
}
