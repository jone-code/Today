import { z } from "zod";

/** identity — 注册 / 登录 */
export const AuthRegisterInputSchema = z.object({
  email: z.string().email().max(191),
  password: z.string().min(6).max(72),
  displayName: z.string().trim().min(1).max(64),
});

export type AuthRegisterInput = z.infer<typeof AuthRegisterInputSchema>;

export const AuthLoginInputSchema = z.object({
  email: z.string().email().max(191),
  password: z.string().min(1).max(72),
});

export type AuthLoginInput = z.infer<typeof AuthLoginInputSchema>;

export const UserDtoSchema = z.object({
  id: z.string(),
  email: z.string().email(),
  displayName: z.string(),
  createdAt: z.string().datetime(),
});

export type UserDto = z.infer<typeof UserDtoSchema>;

export const AuthTokenResponseSchema = z.object({
  token: z.string(),
  tokenType: z.literal("Bearer"),
  user: UserDtoSchema,
});

export type AuthTokenResponse = z.infer<typeof AuthTokenResponseSchema>;
