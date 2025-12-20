export interface User {
    id: string;
    username: string;
    email: string;
    role: string; // Allow any string for flexibility with mock data
    organization?: string;
    permissions: string[];
}

export interface AuthState {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    error: string | null;
}

export interface LoginCredentials {
    username: string;  // Keep for backward compatibility, but will use email
    password: string;
}

export interface RegisterCredentials {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    role: string;
    organization: string;
}

export interface RegisterResponse {
    message: string;
    userId: string;
    email: string;
    role: string;
}

export interface LoginResponse {
    user: User;
    token: string;
}