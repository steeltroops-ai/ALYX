import React, { useState } from 'react';
import LoginForm from './LoginForm';
import RegisterForm from './RegisterForm';

const AuthPage: React.FC = () => {
    const [isLoginMode, setIsLoginMode] = useState(true);

    const switchToRegister = () => setIsLoginMode(false);
    const switchToLogin = () => setIsLoginMode(true);

    return (
        <>
            {isLoginMode ? (
                <LoginForm onSwitchToRegister={switchToRegister} />
            ) : (
                <RegisterForm onSwitchToLogin={switchToLogin} />
            )}
        </>
    );
};

export default AuthPage;