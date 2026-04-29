import axios from "axios";
import { getToken, setToken } from "@/utils/token";
import type { ApiResponse } from "@/types";

const baseURL = import.meta.env.VITE_API_BASE || "/api";

export const http = axios.create({
  baseURL,
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResponse<unknown>;
    if (body && typeof body.code === "number" && body.code !== 0) {
      return Promise.reject(new Error(body.message || "请求失败"));
    }
    return res;
  },
  (err) => {
    const status = err.response?.status;
    const msg =
      (err.response?.data as ApiResponse<unknown> | undefined)?.message ||
      err.message ||
      "网络错误";
    if (status === 401 || status === 403) {
      setToken(null);
      // 自动跳转到登录页
      window.location.href = "/login";
    }
    return Promise.reject(new Error(msg));
  }
);

export async function unwrap<T>(p: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await p;
  return data.data as T;
}
