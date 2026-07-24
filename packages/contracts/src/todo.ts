import { z } from "zod";

/** todo — 待办 */
export const TodoStatusSchema = z.enum(["open", "done"]);

export type TodoStatus = z.infer<typeof TodoStatusSchema>;

export const TodoCreateInputSchema = z.object({
  title: z.string().trim().min(1).max(200),
  note: z.string().trim().max(1000).optional(),
  dueDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional()
    .nullable(),
});

export type TodoCreateInput = z.infer<typeof TodoCreateInputSchema>;

export const TodoUpdateInputSchema = z.object({
  title: z.string().trim().min(1).max(200).optional(),
  note: z.string().trim().max(1000).optional().nullable(),
  dueDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional()
    .nullable(),
  status: TodoStatusSchema.optional(),
});

export type TodoUpdateInput = z.infer<typeof TodoUpdateInputSchema>;

export const TodoDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  title: z.string(),
  note: z.string().nullable(),
  status: TodoStatusSchema,
  dueDate: z.string().nullable(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  completedAt: z.string().datetime().nullable(),
});

export type TodoDto = z.infer<typeof TodoDtoSchema>;

export const TodoListDtoSchema = z.object({
  items: z.array(TodoDtoSchema),
});

export type TodoListDto = z.infer<typeof TodoListDtoSchema>;
