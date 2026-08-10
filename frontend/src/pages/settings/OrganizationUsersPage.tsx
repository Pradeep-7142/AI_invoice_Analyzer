import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { membersApi } from "@/api/membersApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import type { OrgRole } from "@/types/auth";

const ROLES: OrgRole[] = ["ORGANIZATION_ADMIN", "FINANCE_MANAGER", "ACCOUNTANT", "EMPLOYEE", "VIEWER"];

const addMemberSchema = z.object({
  fullName: z.string().min(1, "Full name is required.").max(255),
  email: z.string().min(1, "Email is required.").email("Enter a valid email address."),
  password: z.string().min(8, "Password must be at least 8 characters.").max(128),
  role: z.enum(["ORGANIZATION_ADMIN", "FINANCE_MANAGER", "ACCOUNTANT", "EMPLOYEE", "VIEWER"]),
});
type AddMemberFormValues = z.infer<typeof addMemberSchema>;

export function OrganizationUsersPage() {
  const { role: currentRole, user: currentUser } = useAuth();
  const isAdmin = currentRole === "ORGANIZATION_ADMIN";
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const membersQuery = useQuery({
    queryKey: ["organization-members"],
    queryFn: membersApi.list,
    enabled: isAdmin,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddMemberFormValues>({
    resolver: zodResolver(addMemberSchema),
    defaultValues: { role: "EMPLOYEE" },
  });

  const createMutation = useMutation({
    mutationFn: membersApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organization-members"] });
      setDialogOpen(false);
      reset();
    },
  });

  const roleMutation = useMutation({
    mutationFn: ({ membershipId, role }: { membershipId: string; role: OrgRole }) =>
      membersApi.updateRole(membershipId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization-members"] }),
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const removeMutation = useMutation({
    mutationFn: membersApi.remove,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization-members"] }),
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  if (!isAdmin) {
    return (
      <Box>
        <Typography variant="h4" fontWeight={700}>
          Organization Users
        </Typography>
        <Alert severity="info" sx={{ mt: 2 }}>
          Only organization administrators can manage team members.
        </Alert>
      </Box>
    );
  }

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Organization Users
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage who has access to this organization and what they can do.
          </Typography>
        </Box>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>
          Add member
        </Button>
      </Stack>

      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Role</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {membersQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={4}>
                    <Skeleton variant="text" />
                  </TableCell>
                </TableRow>
              ))}

            {membersQuery.data?.map((member) => (
              <TableRow key={member.membershipId}>
                <TableCell>{member.fullName}</TableCell>
                <TableCell>{member.email}</TableCell>
                <TableCell sx={{ width: 220 }}>
                  <TextField
                    select
                    size="small"
                    fullWidth
                    value={member.role}
                    onChange={(e) =>
                      roleMutation.mutate({ membershipId: member.membershipId, role: e.target.value as OrgRole })
                    }
                  >
                    {ROLES.map((r) => (
                      <MenuItem key={r} value={r}>
                        {r.replaceAll("_", " ")}
                      </MenuItem>
                    ))}
                  </TextField>
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    aria-label="Remove member"
                    disabled={member.userId === currentUser?.id}
                    onClick={() => removeMutation.mutate(member.membershipId)}
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}

            {membersQuery.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No team members yet.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="xs">
        <Box
          component="form"
          onSubmit={handleSubmit((values) => {
            setActionError(null);
            createMutation.mutate(values, {
              onError: (error) => setActionError(apiErrorMessage(error)),
            });
          })}
        >
          <DialogTitle>Add team member</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField
                label="Full name"
                fullWidth
                error={!!errors.fullName}
                helperText={errors.fullName?.message}
                {...register("fullName")}
              />
              <TextField
                label="Email"
                type="email"
                fullWidth
                error={!!errors.email}
                helperText={errors.email?.message}
                {...register("email")}
              />
              <TextField
                label="Temporary password"
                type="password"
                fullWidth
                error={!!errors.password}
                helperText={errors.password?.message ?? "Share this with the new member securely."}
                {...register("password")}
              />
              <TextField select label="Role" fullWidth defaultValue="EMPLOYEE" {...register("role")}>
                {ROLES.map((r) => (
                  <MenuItem key={r} value={r}>
                    {r.replaceAll("_", " ")}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              Add member
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Stack>
  );
}
