// Simple Mock Backend for ALYX Frontend Development
// Run with: node mock-backend.js

const express = require('express');
const cors = require('cors');
const app = express();
const PORT = 8080;

// Middleware
app.use(cors());
app.use(express.json());

// Mock user data
const users = [
    {
        id: 1,
        email: 'admin@alyx.physics.org',
        password: 'admin123',
        role: 'ADMIN',
        name: 'Admin User',
        permissions: ['READ', 'WRITE', 'DELETE', 'ADMIN']
    },
    {
        id: 2,
        email: 'physicist@alyx.physics.org',
        password: 'physicist123',
        role: 'PHYSICIST',
        name: 'Dr. Physics',
        permissions: ['READ', 'WRITE']
    },
    {
        id: 3,
        email: 'analyst@alyx.physics.org',
        password: 'analyst123',
        role: 'ANALYST',
        name: 'Data Analyst',
        permissions: ['READ']
    }
];

// Mock JWT token generation
function generateMockToken(user) {
    return `mock-jwt-token-${user.id}-${Date.now()}`;
}

// Authentication endpoints
app.post('/api/auth/login', (req, res) => {
    const { email, password } = req.body;

    console.log(`Login attempt: ${email}`);

    const user = users.find(u => u.email === email && u.password === password);

    if (user) {
        const token = generateMockToken(user);
        res.json({
            success: true,
            token,
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                role: user.role,
                permissions: user.permissions
            }
        });
    } else {
        res.status(401).json({
            success: false,
            message: 'Invalid credentials'
        });
    }
});

app.post('/api/auth/register', (req, res) => {
    const { email, password, name } = req.body;

    console.log(`Registration attempt: ${email}`);

    // Check if user already exists
    if (users.find(u => u.email === email)) {
        return res.status(400).json({
            success: false,
            message: 'User already exists'
        });
    }

    // Create new user
    const newUser = {
        id: users.length + 1,
        email,
        password,
        name,
        role: 'PHYSICIST',
        permissions: ['READ', 'WRITE']
    };

    users.push(newUser);

    const token = generateMockToken(newUser);
    res.json({
        success: true,
        token,
        user: {
            id: newUser.id,
            email: newUser.email,
            name: newUser.name,
            role: newUser.role,
            permissions: newUser.permissions
        }
    });
});

app.post('/api/auth/logout', (req, res) => {
    res.json({ success: true, message: 'Logged out successfully' });
});

// Protected routes (mock middleware)
app.use('/api/*', (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer mock-jwt-token')) {
        next();
    } else {
        res.status(401).json({ success: false, message: 'Unauthorized' });
    }
});

// Mock API endpoints
app.get('/api/jobs', (req, res) => {
    res.json({
        success: true,
        data: [
            {
                id: 1,
                name: 'Collision Analysis Job #1',
                status: 'RUNNING',
                progress: 65,
                createdAt: new Date().toISOString(),
                estimatedCompletion: new Date(Date.now() + 300000).toISOString()
            },
            {
                id: 2,
                name: 'Particle Track Reconstruction',
                status: 'COMPLETED',
                progress: 100,
                createdAt: new Date(Date.now() - 3600000).toISOString(),
                completedAt: new Date(Date.now() - 1800000).toISOString()
            }
        ]
    });
});

app.get('/api/notebooks', (req, res) => {
    res.json({
        success: true,
        data: [
            {
                id: 1,
                name: 'Physics Analysis Notebook',
                lastModified: new Date().toISOString(),
                collaborators: ['physicist@alyx.physics.org', 'analyst@alyx.physics.org']
            }
        ]
    });
});

app.get('/api/health', (req, res) => {
    res.json({
        status: 'UP',
        timestamp: new Date().toISOString(),
        service: 'mock-backend'
    });
});

// Catch all for unhandled routes
app.use('*', (req, res) => {
    console.log(`Unhandled request: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: 'Endpoint not found',
        path: req.originalUrl
    });
});

// Start server
app.listen(PORT, () => {
    console.log(`🚀 ALYX Mock Backend running on http://localhost:${PORT}`);
    console.log(`📝 Available endpoints:`);
    console.log(`   POST /api/auth/login`);
    console.log(`   POST /api/auth/register`);
    console.log(`   GET  /api/jobs`);
    console.log(`   GET  /api/notebooks`);
    console.log(`   GET  /api/health`);
    console.log(`\n🔑 Demo credentials:`);
    console.log(`   admin@alyx.physics.org / admin123`);
    console.log(`   physicist@alyx.physics.org / physicist123`);
    console.log(`   analyst@alyx.physics.org / analyst123`);
});