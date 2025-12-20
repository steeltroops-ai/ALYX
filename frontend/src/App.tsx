import { Routes, Route } from 'react-router-dom'
import AuthPage from './components/auth/AuthPage'
import ProtectedRoute from './components/auth/ProtectedRoute'
import AppLayout from './components/layout/AppLayout'
import Dashboard from './pages/Dashboard'
import Jobs from './pages/Jobs'
import Visualization from './pages/Visualization'
import QueryBuilder from './pages/QueryBuilder'
import Collaboration from './pages/Collaboration'
import Notebooks from './pages/Notebooks'
import Settings from './pages/Settings'

function App() {
    return (
        <Routes>
            <Route path="/login" element={<AuthPage />} />
            <Route path="/register" element={<AuthPage />} />
            <Route
                path="/*"
                element={
                    <ProtectedRoute>
                        <AppLayout>
                            <Routes>
                                <Route path="/" element={<Dashboard />} />
                                <Route path="/jobs" element={<Jobs />} />
                                <Route path="/visualization" element={<Visualization />} />
                                <Route path="/query" element={<QueryBuilder />} />
                                <Route path="/collaboration" element={<Collaboration />} />
                                <Route path="/notebooks" element={<Notebooks />} />
                                <Route path="/settings" element={<Settings />} />
                            </Routes>
                        </AppLayout>
                    </ProtectedRoute>
                }
            />
        </Routes>
    )
}

export default App