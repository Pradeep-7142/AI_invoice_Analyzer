import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import ShieldOutlinedIcon from "@mui/icons-material/ShieldOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import { usersApi } from "@/api/usersApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import type { UserRole, UserSummary } from "@/types/auth";

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();

  const [selectedUser, setSelectedUser] = useState<UserSummary | null>(null);
  const [newRole, setNewRole] = useState<UserRole>("ROLE_EMPLOYEE");
  const [actionError, setActionError] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: () => usersApi.list(),
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: UserRole }) =>
      usersApi.updateRole(userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setSelectedUser(null);
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const toggleStatusMutation = useMutation({
    mutationFn: (userId: string) => usersApi.toggleStatus(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const users = usersQuery.data ?? [];

  return (
    <Stack spacing={3}>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ sm: "center" }}
        spacing={2}
      >
        <Box>
          <Typography variant="h4" fontWeight={700} color="#0F172A">
            User Accounts & Roles
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage system access, assign administrator or employee roles, and control account status.
          </Typography>
        </Box>
      </Stack>

      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: "14px" }}>
        <Table>
          <TableHead sx={{ bgcolor: "#F8FAFC" }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>User</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Email</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Role</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Registered Date</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, color: "#64748B" }}>
                Actions
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {usersQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={6}>
                    <Skeleton variant="text" height={32} />
                  </TableCell>
                </TableRow>
              ))}

            {!usersQuery.isLoading &&
              users.map((u) => {
                const isSelf = u.id === currentUser?.id;
                return (
                  <TableRow key={u.id} hover>
                    <TableCell>
                      <Stack direction="row" alignItems="center" spacing={1.5}>
                        <Avatar
                          sx={{
                            width: 32,
                            height: 32,
                            fontSize: 13,
                            bgcolor: u.role === "ROLE_ADMIN" ? "#2563EB" : "#0D9488",
                          }}
                        >
                          {u.fullName.charAt(0).toUpperCase()}
                        </Avatar>
                        <Typography variant="body2" fontWeight={600}>
                          {u.fullName} {isSelf && "(You)"}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>
                      <Chip
                        icon={u.role === "ROLE_ADMIN" ? <ShieldOutlinedIcon /> : <PersonOutlineIcon />}
                        label={u.role === "ROLE_ADMIN" ? "ADMIN" : "EMPLOYEE"}
                        size="small"
                        color={u.role === "ROLE_ADMIN" ? "primary" : "default"}
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={u.status}
                        size="small"
                        color={u.status === "ACTIVE" ? "success" : "error"}
                        sx={{ fontSize: "0.75rem", fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "—"}
                    </TableCell>
                    <TableCell align="right">
                      {!isSelf && (
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => {
                              setSelectedUser(u);
                              setNewRole(u.role);
                            }}
                          >
                            Change Role
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            color={u.status === "ACTIVE" ? "error" : "success"}
                            onClick={() => toggleStatusMutation.mutate(u.id)}
                          >
                            {u.status === "ACTIVE" ? "Disable" : "Activate"}
                          </Button>
                        </Stack>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Role Change Modal */}
      <Dialog open={selectedUser !== null} onClose={() => setSelectedUser(null)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Change User Role</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Update role permissions for <strong>{selectedUser?.fullName}</strong> ({selectedUser?.email}).
          </Typography>
          <TextField
            select
            fullWidth
            size="small"
            label="System Role"
            value={newRole}
            onChange={(e) => setNewRole(e.target.value as UserRole)}
          >
            <MenuItem value="ROLE_EMPLOYEE">EMPLOYEE (Can upload and view invoices)</MenuItem>
            <MenuItem value="ROLE_ADMIN">ADMIN (Full access: verify, approve, manage users/vendors)</MenuItem>
          </TextField>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setSelectedUser(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() =>
              selectedUser && updateRoleMutation.mutate({ userId: selectedUser.id, role: newRole })
            }
            disabled={updateRoleMutation.isPending}
          >
            {updateRoleMutation.isPending ? "Saving..." : "Save Role"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
