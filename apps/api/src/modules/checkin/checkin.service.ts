import { Inject, Injectable } from "@nestjs/common";
import type { CheckinCreateInput, CheckinDto } from "@today/contracts";
import { IdentityService } from "../identity/identity.service";

/**
 * checkin — 每日记录写入/读取。
 * 持久化（Prisma）下一步接入；当前内存实现仅用于模块边界可运行。
 */
@Injectable()
export class CheckinService {
  private readonly store = new Map<string, CheckinDto>();

  constructor(
    @Inject(IdentityService) private readonly identity: IdentityService,
  ) {}

  private key(userId: string, date: string) {
    return `${userId}:${date}`;
  }

  todayDate() {
    return new Date().toISOString().slice(0, 10);
  }

  getToday(): CheckinDto | null {
    const userId = this.identity.getCurrentUserId();
    return this.store.get(this.key(userId, this.todayDate())) ?? null;
  }

  upsert(input: CheckinCreateInput): CheckinDto {
    const userId = this.identity.getCurrentUserId();
    const date = input.date ?? this.todayDate();
    const now = new Date().toISOString();
    const existing = this.store.get(this.key(userId, date));
    const dto: CheckinDto = {
      id: existing?.id ?? crypto.randomUUID(),
      userId,
      date,
      rawText: input.rawText,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    this.store.set(this.key(userId, date), dto);
    return dto;
  }

  listRecent(limit = 30): CheckinDto[] {
    const userId = this.identity.getCurrentUserId();
    return [...this.store.values()]
      .filter((c) => c.userId === userId)
      .sort((a, b) => b.date.localeCompare(a.date))
      .slice(0, limit);
  }
}
