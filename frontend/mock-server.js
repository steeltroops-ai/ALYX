const express = require('express');
const cors = require('cors');

const app = express();
const PORT = 8080;

// Middleware
app.use(cors());
app.use(express.json());

// Demo users
const users = {
    'admin@alyx.physics.org': {
        id: 'admin-001',
        email: 'admin@alyx.physics.org',
        password: 'admin123',
        role: 'ADMIN',
        organization: 'ALYX_PHYSICS',
        firstName: 'Admin',
        lastName: 'User'
    },
    'physicist@alyx.physics.org': {
        id: 'physicist-001',
        email: 'physicist@alyx.physics.org',
        password: 'physicist123',
        role: 'PHYSICIST',
        organization: 'CERN',
        firstName: 'Physics',
        lastName: 'Researcher'
    },
    'analyst@alyx.physics.org': {
        id: 'analyst-001',
        email: 'analyst@alyx.physics.org',
        password: 'analyst123',
        role: 'ANALYST',
        organization: 'FERMILAB',
        firstName: 'Data',
        lastName: 'Analyst'
    }
};

// Login endpoint
app.post('/api/auth/login', (req, res) => {
    const { email, password } = req.body;

    console.log(`Login attempt: ${email} with password: ${password ? '[PROVIDED]' : '[MISSING]'}`);
    console.log('Request body:', req.body);

    const user = users[email];
    if (!user || user.password !== password) {
        console.log(`Login failed for ${email}: ${!user ? 'User not found' : 'Invalid password'}`);
        return res.status(401).json({
            error: 'Invalid credentials',
            timestamp: new Date().toISOString()
        });
    }

    // Generate mock JWT token
    const token = `mock-jwt-token-${user.id}-${Date.now()}`;

    console.log(`Login successful for ${email}, returning token`);
    res.json({
        token,
        tokenType: 'Bearer',
        expiresIn: 86400,
        user: {
            id: user.id,
            email: user.email,
            role: user.role,
            organization: user.organization,
            username: user.email // For compatibility
        },
        timestamp: new Date().toISOString()
    });
});

// Register endpoint
app.post('/api/auth/register', (req, res) => {
    const { email, password, firstName, lastName, role, organization } = req.body;

    console.log(`Registration attempt: ${email}`);

    if (users[email]) {
        return res.status(409).json({
            error: 'User already exists',
            timestamp: new Date().toISOString()
        });
    }

    // Create new user
    const userId = `user-${Date.now()}`;
    users[email] = {
        id: userId,
        email,
        password,
        role: role || 'ANALYST',
        organization: organization || 'OTHER',
        firstName,
        lastName
    };

    res.status(201).json({
        message: 'User registered successfully',
        userId,
        email,
        role: role || 'ANALYST',
        timestamp: new Date().toISOString()
    });
});

// Token verification endpoint (for ProtectedRoute)
app.get('/api/auth/verify', (req, res) => {
    const authHeader = req.headers.authorization;

    console.log('Token verification request:', authHeader ? 'Token provided' : 'No token');

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({
            error: 'Invalid token format'
        });
    }

    const token = authHeader.substring(7);

    // Mock verification - in real app, verify JWT
    if (token.startsWith('mock-jwt-token-')) {
        const userId = token.split('-')[3];
        const user = Object.values(users).find(u => u.id === userId);

        if (user) {
            console.log(`Token verified for user: ${user.email}`);
            return res.json({
                id: user.id,
                email: user.email,
                role: user.role,
                organization: user.organization,
                username: user.email,
                permissions: ['READ', 'WRITE', 'ADMIN'] // Mock permissions
            });
        }
    }

    console.log('Token verification failed');
    res.status(401).json({
        error: 'Token expired or invalid'
    });
});

// Token validation endpoint (legacy)
app.post('/api/auth/validate', (req, res) => {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({
            valid: false,
            error: 'Invalid token format'
        });
    }

    const token = authHeader.substring(7);

    // Mock validation - in real app, verify JWT
    if (token.startsWith('mock-jwt-token-')) {
        const userId = token.split('-')[3];
        const user = Object.values(users).find(u => u.id === userId);

        if (user) {
            return res.json({
                valid: true,
                userId: user.id,
                role: user.role,
                organization: user.organization,
                timestamp: new Date().toISOString()
            });
        }
    }

    res.status(401).json({
        valid: false,
        error: 'Token expired or invalid'
    });
});

// Health check
app.get('/actuator/health', (req, res) => {
    res.json({ status: 'UP', service: 'mock-api-gateway' });
});

// Start server
app.listen(PORT, () => {
    console.log(`🚀 Mock ALYX API Gateway running on http://localhost:${PORT}`);
    console.log(`📝 Available demo accounts:`);
    Object.values(users).forEach(user => {
        console.log(`   ${user.email} / ${user.password} (${user.role})`);
    });
    console.log(`🔗 Frontend should be running on http://localhost:3000`);
});