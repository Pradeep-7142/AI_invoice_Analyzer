import { useState } from "react";
import {
  AppBar,
  Avatar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import DashboardIcon from "@mui/icons-material/DashboardOutlined";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLongOutlined";
import StorefrontIcon from "@mui/icons-material/StorefrontOutlined";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import GroupOutlinedIcon from "@mui/icons-material/GroupOutlined";
import LogoutIcon from "@mui/icons-material/Logout";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/AuthContext";

const DRAWER_WIDTH = 250;

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null);
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    setUserMenuAnchor(null);
    await logout();
    navigate("/login", { replace: true });
  };

  const navItems = [
    { label: "Dashboard", to: "/dashboard", icon: <DashboardIcon /> },
    { label: "Invoices", to: "/invoices", icon: <ReceiptLongIcon /> },
    { label: "Vendors", to: "/vendors", icon: <StorefrontIcon /> },
    { label: "AI Assistant", to: "/ai-assistant", icon: <AutoAwesomeIcon color="primary" /> },
    ...(isAdmin ? [{ label: "Users & Roles", to: "/users", icon: <GroupOutlinedIcon /> }] : []),
  ];

  const drawerContent = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%", bgcolor: "#FFFFFF" }}>
      <Toolbar sx={{ px: 3, display: "flex", alignItems: "center", gap: 1.5 }}>
        <Box
          sx={{
            width: 34,
            height: 34,
            borderRadius: "9px",
            background: "linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#FFF",
            boxShadow: "0 2px 6px rgba(37,99,235,0.3)",
          }}
        >
          <AutoAwesomeIcon sx={{ fontSize: 20 }} />
        </Box>
        <Typography variant="h6" fontWeight={700} sx={{ letterSpacing: "-0.02em", color: "#0F172A" }}>
          InvoiceIQ
        </Typography>
      </Toolbar>
      <Divider sx={{ borderColor: "#F1F5F9" }} />
      <List sx={{ flexGrow: 1, px: 2, py: 2 }}>
        {navItems.map((item) => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            onClick={() => setMobileOpen(false)}
            sx={{
              borderRadius: "10px",
              mb: 0.75,
              py: 1.2,
              px: 2,
              color: "#475569",
              "&.active": {
                backgroundColor: "#EFF6FF",
                color: "#1D4ED8",
                fontWeight: 600,
                "& .MuiListItemIcon-root": {
                  color: "#2563EB",
                },
              },
              "&:hover": {
                backgroundColor: "#F8FAFC",
              },
            }}
          >
            <ListItemIcon sx={{ minWidth: 38, color: "#64748B" }}>{item.icon}</ListItemIcon>
            <ListItemText
              primary={item.label}
              primaryTypographyProps={{ fontSize: "0.92rem", fontWeight: "inherit" }}
            />
          </ListItemButton>
        ))}
      </List>
      <Divider sx={{ borderColor: "#F1F5F9" }} />
      <Box sx={{ p: 2 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: "12px",
            bgcolor: "#F8FAFC",
            border: "1px solid #E2E8F0",
            display: "flex",
            alignItems: "center",
            gap: 1.5,
          }}
        >
          <Avatar
            sx={{
              width: 36,
              height: 36,
              fontSize: 14,
              fontWeight: 600,
              bgcolor: isAdmin ? "#2563EB" : "#0D9488",
            }}
          >
            {user?.fullName?.charAt(0).toUpperCase()}
          </Avatar>
          <Box sx={{ minWidth: 0, flexGrow: 1 }}>
            <Typography variant="body2" fontWeight={600} noWrap>
              {user?.fullName}
            </Typography>
            <Chip
              label={isAdmin ? "ADMIN" : "EMPLOYEE"}
              size="small"
              sx={{
                height: 18,
                fontSize: "0.65rem",
                fontWeight: 700,
                bgcolor: isAdmin ? "#DBEAFE" : "#CCFBF1",
                color: isAdmin ? "#1E40AF" : "#0F766E",
                mt: 0.25,
              }}
            />
          </Box>
          <IconButton size="small" onClick={handleLogout} title="Logout" sx={{ color: "#94A3B8" }}>
            <LogoutIcon fontSize="small" />
          </IconButton>
        </Box>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { sm: `${DRAWER_WIDTH}px` },
          borderBottom: "1px solid #E2E8F0",
          backgroundColor: "#FFFFFF",
        }}
      >
        <Toolbar sx={{ justifyContent: "space-between" }}>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setMobileOpen(!mobileOpen)}
            sx={{ mr: 2, display: { sm: "none" }, color: "#0F172A" }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Typography variant="body2" color="text.secondary" sx={{ display: { xs: "none", sm: "block" } }}>
              Smart Invoice Intelligence & OCR Extraction Platform
            </Typography>
          </Box>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Chip
              label={isAdmin ? "System Admin" : "Employee"}
              variant="outlined"
              size="small"
              color={isAdmin ? "primary" : "secondary"}
              sx={{ fontWeight: 600 }}
            />
            <IconButton onClick={(e) => setUserMenuAnchor(e.currentTarget)} size="small">
              <Avatar sx={{ width: 34, height: 34, fontSize: 14, bgcolor: "#3B82F6" }}>
                {user?.fullName?.charAt(0).toUpperCase()}
              </Avatar>
            </IconButton>
            <Menu
              anchorEl={userMenuAnchor}
              open={!!userMenuAnchor}
              onClose={() => setUserMenuAnchor(null)}
              transformOrigin={{ horizontal: "right", vertical: "top" }}
              anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
              PaperProps={{
                sx: { width: 220, mt: 1, borderRadius: "10px", boxShadow: "0 10px 25px rgba(0,0,0,0.1)" },
              }}
            >
              <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={600} noWrap>
                  {user?.fullName}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap>
                  {user?.email}
                </Typography>
              </Box>
              <Divider />
              <MenuItem onClick={handleLogout} sx={{ color: "error.main", py: 1 }}>
                <ListItemIcon sx={{ color: "error.main", minWidth: 32 }}>
                  <LogoutIcon fontSize="small" />
                </ListItemIcon>
                Sign out
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { sm: DRAWER_WIDTH }, flexShrink: { sm: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", sm: "none" },
            "& .MuiDrawer-paper": { width: DRAWER_WIDTH, boxSizing: "border-box" },
          }}
        >
          {drawerContent}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: "none", sm: "block" },
            "& .MuiDrawer-paper": {
              width: DRAWER_WIDTH,
              boxSizing: "border-box",
              borderRight: "1px solid #E2E8F0",
            },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3.5 },
          width: { sm: `calc(100% - ${DRAWER_WIDTH}px)` },
          minHeight: "100vh",
          backgroundColor: "#F8FAFC",
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
