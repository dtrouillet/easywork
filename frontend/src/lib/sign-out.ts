import { getSession, signOut } from "next-auth/react";

/**
 * Clears the local NextAuth session, then hands off to the keycloak-logout
 * route handler to also terminate the Keycloak IdP session (RP-Initiated
 * Logout) — plain next-auth signOut() alone leaves the user signed into
 * Keycloak's SSO session. The id_token is read *before* signOut() runs,
 * since signOut() clears the session that carries it.
 */
export async function fullSignOut(callbackUrl = "/login") {
  const session = await getSession();
  await signOut({ redirect: false });

  const params = new URLSearchParams({ callbackUrl });
  if (session?.idToken) {
    params.set("idToken", session.idToken);
  }
  window.location.href = `/api/auth/keycloak-logout?${params.toString()}`;
}
