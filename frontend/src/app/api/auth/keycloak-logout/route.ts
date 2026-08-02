import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";

/**
 * Signing out of NextAuth only clears our own session cookie — Keycloak's SSO
 * session stays alive, so a subsequent "Sign in with SSO" silently re-authenticates
 * without a login prompt. This performs RP-Initiated Logout against Keycloak's
 * end_session_endpoint so the IdP session is actually terminated too.
 */
export async function GET(request: Request) {
  const requestUrl = new URL(request.url);

  const rawCallback = requestUrl.searchParams.get("callbackUrl") ?? "/login";
  // Must be a same-origin relative path — "//evil.com" also starts with "/" but
  // browsers resolve it as protocol-relative, so reject that too.
  const callbackPath =
    rawCallback.startsWith("/") && !rawCallback.startsWith("//") ? rawCallback : "/login";
  const postLogoutRedirectUri = new URL(callbackPath, requestUrl.origin).toString();

  // The caller passes the id_token explicitly because by the time it reaches
  // here the NextAuth session has usually already been cleared (see
  // lib/sign-out.ts) — fall back to the live session for any other caller.
  const idTokenFromQuery = requestUrl.searchParams.get("idToken");
  const session = idTokenFromQuery ? null : await auth();

  const params = new URLSearchParams({ post_logout_redirect_uri: postLogoutRedirectUri });
  const idToken = idTokenFromQuery ?? session?.idToken;
  if (idToken) {
    params.set("id_token_hint", idToken);
  }

  const logoutUrl = `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/logout?${params.toString()}`;
  return NextResponse.redirect(logoutUrl);
}
