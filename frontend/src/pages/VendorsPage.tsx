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
  Grid,
  IconButton,
  InputAdornment,
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
import SearchIcon from "@mui/icons-material/Search";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import ArchiveOutlinedIcon from "@mui/icons-material/ArchiveOutlined";
import { vendorsApi, type VendorPayload } from "@/api/vendorsApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import type { Vendor } from "@/types/invoice";

const vendorSchema = z.object({
  name: z.string().min(1, "Vendor name is required.").max(255),
  email: z.string().email("Enter a valid email address.").or(z.literal("")).optional(),
  phone: z.string().max(50).optional(),
  address: z.string().optional(),
  gstin: z.string().max(20).optional(),
  taxId: z.string().max(50).optional(),
  category: z.string().max(100).optional(),
  notes: z.string().optional(),
});
type VendorFormValues = z.infer<typeof vendorSchema>;

const CAN_MANAGE_VENDORS = new Set(["ORGANIZATION_ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"]);

export function VendorsPage() {
  const { role } = useAuth();
  const canManage = role !== null && CAN_MANAGE_VENDORS.has(role);
  const queryClient = useQueryClient();

  const [search, setSearch] = useState("");
  const [dialogVendor, setDialogVendor] = useState<Vendor | "new" | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const vendorsQuery = useQuery({
    queryKey: ["vendors", search],
    queryFn: () => vendorsApi.list(search || undefined),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<VendorFormValues>({ resolver: zodResolver(vendorSchema) });

  const openDialog = (vendor: Vendor | "new") => {
    setActionError(null);
    setDialogVendor(vendor);
    reset(
      vendor === "new"
        ? { name: "", email: "", phone: "", address: "", gstin: "", taxId: "", category: "", notes: "" }
        : {
            name: vendor.name,
            email: vendor.email ?? "",
            phone: vendor.phone ?? "",
            address: vendor.address ?? "",
            gstin: vendor.gstin ?? "",
            taxId: vendor.taxId ?? "",
            category: vendor.category ?? "",
            notes: vendor.notes ?? "",
          }
    );
  };

  const saveMutation = useMutation({
    mutationFn: (payload: VendorPayload) =>
      dialogVendor === "new" || dialogVendor === null
        ? vendorsApi.create(payload)
        : vendorsApi.update(dialogVendor.id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["vendors"] });
      setDialogVendor(null);
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const archiveMutation = useMutation({
    mutationFn: vendorsApi.archive,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["vendors"] }),
  });

  const vendors = vendorsQuery.data?.content ?? [];

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Vendors
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Vendors referenced by your organization's invoices.
          </Typography>
        </Box>
        {canManage && <Button variant="contained" onClick={() => openDialog("new")}>Add vendor</Button>}
      </Stack>

      <TextField
        placeholder="Search vendors..."
        size="small"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        sx={{ maxWidth: 320 }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
      />

      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Category</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Phone</TableCell>
              {canManage && <TableCell align="right">Actions</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {vendorsQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={5}><Skeleton variant="text" /></TableCell>
                </TableRow>
              ))}

            {vendors.map((vendor) => (
              <TableRow key={vendor.id} hover>
                <TableCell>{vendor.name}</TableCell>
                <TableCell>{vendor.category ?? "—"}</TableCell>
                <TableCell>{vendor.email ?? "—"}</TableCell>
                <TableCell>{vendor.phone ?? "—"}</TableCell>
                {canManage && (
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => openDialog(vendor)}>
                      <EditOutlinedIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => {
                        if (window.confirm(`Archive vendor "${vendor.name}"? It will be hidden from lists but historical invoices are unaffected.`)) {
                          archiveMutation.mutate(vendor.id);
                        }
                      }}
                    >
                      <ArchiveOutlinedIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                )}
              </TableRow>
            ))}

            {!vendorsQuery.isLoading && vendors.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No vendors yet. {canManage && "Add your first vendor to start linking invoices to it."}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={dialogVendor !== null} onClose={() => setDialogVendor(null)} fullWidth maxWidth="sm">
        <Box
          component="form"
          onSubmit={handleSubmit((values) => {
            setActionError(null);
            saveMutation.mutate(values);
          })}
        >
          <DialogTitle>{dialogVendor === "new" ? "Add vendor" : "Edit vendor"}</DialogTitle>
          <DialogContent>
            {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid item xs={12}>
                <TextField label="Name" fullWidth error={!!errors.name} helperText={errors.name?.message} {...register("name")} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="Email" fullWidth error={!!errors.email} helperText={errors.email?.message} {...register("email")} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="Phone" fullWidth {...register("phone")} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Address" fullWidth multiline minRows={2} {...register("address")} />
              </Grid>
              <Grid item xs={4}>
                <TextField label="Category" fullWidth {...register("category")} />
              </Grid>
              <Grid item xs={4}>
                <TextField label="GSTIN" fullWidth {...register("gstin")} />
              </Grid>
              <Grid item xs={4}>
                <TextField label="Tax ID" fullWidth {...register("taxId")} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Notes" fullWidth multiline minRows={2} {...register("notes")} />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogVendor(null)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              Save
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Stack>
  );
}
