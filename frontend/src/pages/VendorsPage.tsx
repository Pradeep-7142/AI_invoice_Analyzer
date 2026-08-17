import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import {
  Alert,
  Box,
  Button,
  Chip,
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
import AddIcon from "@mui/icons-material/Add";
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

export function VendorsPage() {
  const { isAdmin } = useAuth();
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

  const vendors: Vendor[] = Array.isArray(vendorsQuery.data) ? vendorsQuery.data : [];

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
            Vendors Directory
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage vendors, GSTIN details, and track vendor relationships.
          </Typography>
        </Box>
        {isAdmin && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => openDialog("new")}>
            Add Vendor
          </Button>
        )}
      </Stack>

      <TextField
        placeholder="Search vendors by name..."
        size="small"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        sx={{ maxWidth: 360 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" color="action" />
            </InputAdornment>
          ),
        }}
      />

      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: "14px" }}>
        <Table>
          <TableHead sx={{ bgcolor: "#F8FAFC" }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Vendor Name</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Category</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>GSTIN / Tax ID</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Email</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Phone</TableCell>
              {isAdmin && <TableCell align="right" sx={{ fontWeight: 600, color: "#64748B" }}>Actions</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {vendorsQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={6}>
                    <Skeleton variant="text" height={32} />
                  </TableCell>
                </TableRow>
              ))}

            {!vendorsQuery.isLoading &&
              vendors.map((vendor) => (
                <TableRow key={vendor.id} hover>
                  <TableCell sx={{ fontWeight: 600, color: "#0F172A" }}>{vendor.name}</TableCell>
                  <TableCell>
                    {vendor.category ? (
                      <Chip label={vendor.category} size="small" variant="outlined" sx={{ fontSize: "0.75rem" }} />
                    ) : (
                      "—"
                    )}
                  </TableCell>
                  <TableCell>{vendor.gstin || vendor.taxId || "—"}</TableCell>
                  <TableCell>{vendor.email || "—"}</TableCell>
                  <TableCell>{vendor.phone || "—"}</TableCell>
                  {isAdmin && (
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => openDialog(vendor)} color="primary">
                        <EditOutlinedIcon fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => {
                          if (window.confirm(`Archive vendor "${vendor.name}"?`)) {
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
                <TableCell colSpan={6}>
                  <Box sx={{ py: 6, textAlign: "center" }}>
                    <Typography variant="body1" fontWeight={600} color="text.secondary">
                      No vendors found
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {isAdmin ? "Click 'Add Vendor' to create your first vendor." : "No vendors registered in the system."}
                    </Typography>
                  </Box>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Add / Edit Vendor Dialog */}
      <Dialog open={dialogVendor !== null} onClose={() => setDialogVendor(null)} fullWidth maxWidth="sm">
        <Box
          component="form"
          onSubmit={handleSubmit((values) => {
            setActionError(null);
            saveMutation.mutate(values);
          })}
        >
          <DialogTitle fontWeight={700}>
            {dialogVendor === "new" ? "Add New Vendor" : `Edit Vendor: ${dialogVendor?.name}`}
          </DialogTitle>
          <DialogContent>
            {actionError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {actionError}
              </Alert>
            )}
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid item xs={12}>
                <TextField
                  label="Vendor Name"
                  fullWidth
                  error={!!errors.name}
                  helperText={errors.name?.message}
                  {...register("name")}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  label="Email"
                  type="email"
                  fullWidth
                  error={!!errors.email}
                  helperText={errors.email?.message}
                  {...register("email")}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField label="Phone" fullWidth {...register("phone")} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Address" fullWidth multiline minRows={2} {...register("address")} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField label="Category (e.g. Cloud, Utilities)" fullWidth {...register("category")} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField label="GSTIN" fullWidth {...register("gstin")} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField label="Tax ID" fullWidth {...register("taxId")} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Notes" fullWidth multiline minRows={2} {...register("notes")} />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setDialogVendor(null)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting || saveMutation.isPending}>
              {saveMutation.isPending ? "Saving..." : "Save Vendor"}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Stack>
  );
}
