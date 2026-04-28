import { http, unwrap } from "./http";
import type { LoginRequest, LoginResponse, User } from "@/types";

export function login(body: LoginRequest) {
  return unwrap<LoginResponse>(http.post("/auth/login", body));
}

export function fetchMe() {
  return unwrap<User>(http.get("/auth/me"));
}
