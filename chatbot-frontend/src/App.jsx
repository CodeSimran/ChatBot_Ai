import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import './App.css';

const Particle = ({ style }) => (
    <motion.div 
        className="particle" 
        style={style} 
        initial={{ opacity: 0, scale: 0 }}
        animate={{ opacity: style.opacity, scale: 1 }}
        transition={{ duration: 1.5, delay: Math.random() * 2 }}
    />
);

function App() {
    const [messages, setMessages] = useState([]);
    const [inputValue, setInputValue] = useState('');
    const [loading, setLoading] = useState(false);
    const [particles] = useState(() =>
        Array.from({ length: 60 }, (_, i) => ({
            id: i,
            style: {
                left: `${Math.random() * 100}%`,
                top: `${Math.random() * 100}%`,
                width: `${Math.random() * 8 + 2}px`,
                height: `${Math.random() * 8 + 2}px`,
                animationDelay: `${Math.random() * 6}s`,
                animationDuration: `${Math.random() * 15 + 6}s`,
                opacity: Math.random() * 0.7 + 0.15,
                filter: `blur(${Math.random() * 3}px)`
            }
        }))
    );
    const messagesContainerRef = useRef(null);
    const inputRef = useRef(null);

    const scrollToBottom = () => {
        if (messagesContainerRef.current) {
            messagesContainerRef.current.scrollTo({
                top: messagesContainerRef.current.scrollHeight,
                behavior: 'smooth'
            });
        }
    };

    useEffect(() => {
        // Slight delay to ensure DOM is updated before scrolling
        const timeout = setTimeout(() => scrollToBottom(), 100);
        return () => clearTimeout(timeout);
    }, [messages, loading]);

    const formatTime = (date) => {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    };

    const handleSendMessage = async (e) => {
        e.preventDefault();
        if (!inputValue.trim()) return;

        const userMessage = {
            id: Date.now(),
            text: inputValue,
            sender: 'user',
            timestamp: new Date()
        };

        setMessages(prev => [...prev, userMessage]);
        const sentText = inputValue;
        setInputValue('');
        setLoading(true);

        try {
            const response = await fetch('http://localhost:8080/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: sentText
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || `API error: ${response.status}`);
            }

            const replyText = await response.text();
            const aiMessage = {
                id: Date.now() + 1,
                text: replyText || 'Sorry, I could not understand that.',
                sender: 'ai',
                timestamp: new Date()
            };
            setMessages(prev => [...prev, aiMessage]);
        } catch (error) {
            console.error('Error:', error);
            const errorMessage = {
                id: Date.now() + 1,
                text: typeof error.message === 'string' && error.message.includes('Error:')
                    ? 'Backend Error: ' + error.message
                    : '⚠️ Could not connect to the server. Make sure the backend is running on http://localhost:8080',
                sender: 'ai',
                timestamp: new Date(),
                isError: true
            };
            setMessages(prev => [...prev, errorMessage]);
        } finally {
            setLoading(false);
            inputRef.current?.focus();
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            handleSendMessage(e);
        }
    };

    return (
        <div className="app-wrapper">
            {/* Background Particles */}
            <div className="particles-bg">
                {particles.map(p => <Particle key={p.id} style={p.style} />)}
            </div>

            {/* Floating Gradient Orbs */}
            <motion.div 
                className="orb orb-1"
                animate={{ 
                    scale: [1, 1.15, 1],
                    x: [0, 40, 0],
                    y: [0, -40, 0]
                }}
                transition={{ duration: 18, repeat: Infinity, ease: "easeInOut" }}
            />
            <motion.div 
                className="orb orb-2"
                animate={{ 
                    scale: [1, 1.25, 1],
                    x: [0, -50, 0],
                    y: [0, 50, 0]
                }}
                transition={{ duration: 22, repeat: Infinity, ease: "easeInOut", delay: 2 }}
            />
            <motion.div 
                className="orb orb-3"
                animate={{ 
                    scale: [0.8, 1.1, 0.8],
                    rotate: [0, 90, 0]
                }}
                transition={{ duration: 25, repeat: Infinity, ease: "easeInOut" }}
            />

            {/* Absolute Centering Wrapper */}
            <div className="chat-container-wrapper">
                {/* Main Chat Container */}
                <motion.div 
                    className="chat-container"
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.5, ease: "easeOut" }}
                >
                    {/* Header */}
                    <div className="chat-header">
                        <motion.div 
                            className="header-glow"
                            animate={{ opacity: [0.3, 0.6, 0.3] }}
                            transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
                        />
                        <div className="header-content">
                            <motion.div 
                                className="bot-avatar"
                                whileHover={{ scale: 1.08, rotate: 5 }}
                                transition={{ type: "spring", stiffness: 400, damping: 10 }}
                            >
                                <span className="bot-icon">🤖</span>
                                <div className="status-dot" />
                            </motion.div>
                            <div className="header-text">
                                <h1 className="header-title">AI Assistant</h1>
                                <p className="subtitle">
                                    <span className="pulse-dot" />
                                    Online & Ready
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Messages List */}
                    <div className="messages-container" ref={messagesContainerRef}>
                        <AnimatePresence>
                            {messages.length === 0 ? (
                                <motion.div 
                                    key="welcome"
                                    className="welcome-message"
                                    initial={{ opacity: 0, y: 20 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0, scale: 0.9, transition: { duration: 0.2 } }}
                                    transition={{ duration: 0.5 }}
                                >
                                    <motion.div 
                                        className="welcome-icon-wrap"
                                        animate={{ y: [0, -12, 0] }}
                                        transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
                                    >
                                        <span className="welcome-icon">✨</span>
                                    </motion.div>
                                    <h2 className="welcome-title">How can I help you today?</h2>
                                    <p className="welcome-sub">Powered by cutting-edge AI technology</p>
                                    <div className="suggestion-chips">
                                        {['Tell me a joke 😄', 'What is AI? 🤖', 'Write a poem 🌸'].map((s, i) => (
                                            <motion.button
                                                key={i}
                                                className="chip"
                                                onClick={() => setInputValue(s)}
                                                initial={{ opacity: 0, y: 20 }}
                                                animate={{ opacity: 1, y: 0 }}
                                                transition={{ delay: i * 0.1 + 0.3, type: 'spring', stiffness: 300 }}
                                                whileHover={{ scale: 1.05, y: -2, backgroundColor: 'rgba(139, 92, 246, 0.25)' }}
                                                whileTap={{ scale: 0.95 }}
                                            >
                                                {s}
                                            </motion.button>
                                        ))}
                                    </div>
                                </motion.div>
                            ) : (
                                messages.map((message) => (
                                    <motion.div
                                        key={message.id}
                                        className={`message ${message.sender} ${message.isError ? 'error' : ''}`}
                                        initial={{ opacity: 0, x: message.sender === 'user' ? 40 : -40, scale: 0.95 }}
                                        animate={{ opacity: 1, x: 0, scale: 1 }}
                                        transition={{ type: "spring", stiffness: 350, damping: 25 }}
                                        layout
                                    >
                                        {message.sender === 'ai' && (
                                            <motion.div 
                                                className="avatar ai-avatar"
                                                initial={{ scale: 0 }}
                                                animate={{ scale: 1 }}
                                                transition={{ type: "spring", delay: 0.1 }}
                                            >🤖</motion.div>
                                        )}
                                        <div className="message-bubble">
                                            <div className="message-content">
                                                <p>{message.text}</p>
                                            </div>
                                            <span className="timestamp">{formatTime(message.timestamp)}</span>
                                        </div>
                                        {message.sender === 'user' && (
                                            <motion.div 
                                                className="avatar user-avatar"
                                                initial={{ scale: 0 }}
                                                animate={{ scale: 1 }}
                                                transition={{ type: "spring", delay: 0.1 }}
                                            >👤</motion.div>
                                        )}
                                    </motion.div>
                                ))
                            )}

                            {loading && (
                                <motion.div 
                                    key="typing"
                                    className="message ai"
                                    initial={{ opacity: 0, y: 20, scale: 0.95 }}
                                    animate={{ opacity: 1, y: 0, scale: 1 }}
                                    exit={{ opacity: 0, scale: 0.9 }}
                                    transition={{ type: "spring", stiffness: 350, damping: 25 }}
                                    layout
                                >
                                    <div className="avatar ai-avatar">🤖</div>
                                    <div className="message-bubble">
                                        <div className="message-content typing-bubble">
                                            <div className="typing-indicator">
                                                <motion.span animate={{ y: [0, -8, 0], backgroundColor: ['#a78bfa', '#f472b6', '#a78bfa'] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0 }} />
                                                <motion.span animate={{ y: [0, -8, 0], backgroundColor: ['#a78bfa', '#f472b6', '#a78bfa'] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0.2 }} />
                                                <motion.span animate={{ y: [0, -8, 0], backgroundColor: ['#a78bfa', '#f472b6', '#a78bfa'] }} transition={{ repeat: Infinity, duration: 1.2, delay: 0.4 }} />
                                            </div>
                                        </div>
                                    </div>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </div>

                    {/* Input form */}
                    <form className="input-form" onSubmit={handleSendMessage}>
                        <div className="input-wrapper">
                            <input
                                ref={inputRef}
                                type="text"
                                value={inputValue}
                                onChange={(e) => setInputValue(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="Ask me anything..."
                                disabled={loading}
                                className="message-input"
                                id="chat-input"
                                autoComplete="off"
                            />
                            <AnimatePresence>
                                {inputValue && (
                                    <motion.button
                                        type="button"
                                        className="clear-btn"
                                        onClick={() => setInputValue('')}
                                        initial={{ opacity: 0, scale: 0.5, rotate: -90 }}
                                        animate={{ opacity: 1, scale: 1, rotate: 0 }}
                                        exit={{ opacity: 0, scale: 0.5, rotate: 90 }}
                                        whileHover={{ scale: 1.1 }}
                                        whileTap={{ scale: 0.9 }}
                                    >✕</motion.button>
                                )}
                            </AnimatePresence>
                        </div>
                        <motion.button
                            type="submit"
                            disabled={loading || !inputValue.trim()}
                            className="send-button"
                            whileHover={!loading && inputValue.trim() ? { scale: 1.05, boxShadow: "0 8px 25px rgba(139, 92, 246, 0.5)" } : {}}
                            whileTap={!loading && inputValue.trim() ? { scale: 0.95 } : {}}
                        >
                            <span className="send-icon">{loading ? '⏳' : '➤'}</span>
                        </motion.button>
                    </form>
                </motion.div>
            </div>
        </div>
    );
}

export default App;
