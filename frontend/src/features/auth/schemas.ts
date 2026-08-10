import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().min(1, "Email is required.").email("Enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  organizationName: z.string().min(1, "Organization name is required.").max(255),
  fullName: z.string().min(1, "Full name is required.").max(255),
  email: z.string().min(1, "Email is required.").email("Enter a valid email address."),
  password: z.string().min(8, "Password must be at least 8 characters.").max(128),
});
export type RegisterFormValues = z.infer<typeof registerSchema>;
