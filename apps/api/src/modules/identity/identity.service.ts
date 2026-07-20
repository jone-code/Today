import { Injectable } from "@nestjs/common";

@Injectable()
export class IdentityService {
  /** MVP：无登录，统一开发用户，表结构仍带 userId */
  getCurrentUserId(): string {
    return process.env.TODAY_DEV_USER_ID ?? "dev-user";
  }
}
