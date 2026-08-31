import { useState, useEffect, useRef, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, AlertCircle, ArrowRight, User, Mail, Lock } from 'lucide-react'
import { toast } from 'sonner'
import AuthGraphics from '../components/layout/AuthGraphics'
import BackgroundWorkflowStream from '../components/layout/BackgroundWorkflowStream'
import { useAuthStore } from '../store/authStore'
import { validateEmail, validatePassword, validateRequired } from '../utils/validators'

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

export default function Signup() {
  const navigate = useNavigate()
  const signup = useAuthStore((state) => state.signup)
  const loginWithGoogle = useAuthStore((state) => state.loginWithGoogle)
  const loading = useAuthStore((state) => state.loading)

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [googleLoading, setGoogleLoading] = useState(false)
  const [isHovered, setIsHovered] = useState(false)
  const googleBtnRef = useRef(null)

    const googleCredentialCallback = useCallback(async (response) => {
        if (!response?.credential) {
            toast.error('Google sign-up was cancelled or failed.')
            return
        }

        setGoogleLoading(true)
        try {
            await loginWithGoogle(response.credential)
            toast.success('Account created with Google!')
            navigate('/dashboard')
        } catch (err) {
            toast.error(err.message || 'Google sign-up failed. Please try again.')
        } finally {
            setGoogleLoading(false)
        }
    }, [loginWithGoogle, navigate])

  const initGoogleButton = useCallback(() => {
    if (!window.google?.accounts?.id) return
    if (!GOOGLE_CLIENT_ID || GOOGLE_CLIENT_ID === 'YOUR_GOOGLE_CLIENT_ID_HERE') return

    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: googleCredentialCallback,
      auto_select: false,
      cancel_on_tap_outside: true,
      ux_mode: 'popup',
    })

    if (googleBtnRef.current) {
      googleBtnRef.current.innerHTML = ''
      const width = googleBtnRef.current.getBoundingClientRect().width || 368
      window.google.accounts.id.renderButton(googleBtnRef.current, {
        theme: 'outline',
        size: 'large',
        width: Math.floor(width),
        text: 'signup_with',
        shape: 'rectangular',
      })
    }
  }, [googleCredentialCallback])

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID || GOOGLE_CLIENT_ID === 'YOUR_GOOGLE_CLIENT_ID_HERE') return

    if (window.google?.accounts?.id) {
      setTimeout(initGoogleButton, 50)
      return
    }

    const onLoad = () => setTimeout(initGoogleButton, 50)
    const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]')
    if (script) {
      script.addEventListener('load', onLoad)
      return () => script.removeEventListener('load', onLoad)
    }
  }, [initGoogleButton])

  const handleGoogleClickFallback = () => {
    if (!GOOGLE_CLIENT_ID || GOOGLE_CLIENT_ID === 'YOUR_GOOGLE_CLIENT_ID_HERE') {
      toast.error('Google login is not configured. Add VITE_GOOGLE_CLIENT_ID to .env.')
      return
    }
    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt((notification) => {
        if (notification.isNotDisplayed()) {
          toast.error('Google popup was blocked.')
        } else if (notification.isSkippedMoment()) {
          toast.error('Google sign-in was skipped.')
        }
      })
    } else {
      toast.error('Google SDK not loaded yet.')
    }
  }

  const handleSignup = async (e) => {
    e.preventDefault()

    if (!validateRequired(name)) {
      toast.error('Please enter your full name')
      return
    }

    if (!validateEmail(email)) {
      toast.error('Please enter a valid email address')
      return
    }

    if (!validatePassword(password)) {
      toast.error('Password must be at least 6 characters long')
      return
    }

    try {
      await signup(name, email, password)
      toast.success('Account created successfully!')
      navigate('/dashboard')
    } catch (err) {
      toast.error(err.message || 'Registration failed. Please try again.')
    }
  }

  const isGoogleConfigured = GOOGLE_CLIENT_ID && GOOGLE_CLIENT_ID !== 'YOUR_GOOGLE_CLIENT_ID_HERE'

  return (
    <div style={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: '1.15fr 1fr', background: '#f3f6fc', position: 'relative', overflow: 'hidden' }}>
      
      {/* Low-Glow Background Stream */}
      <BackgroundWorkflowStream />

      {/* Left side: Hero & 3D Interactive Pipeline Motion */}
      <AuthGraphics />

      {/* Right side: Floating White Form Card */}
      <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: '40px 48px', position: 'relative', zIndex: 10 }}>
        
        <div 
          style={{ 
            width: '100%', 
            maxWidth: '430px', 
            background: '#ffffff', 
            borderRadius: '32px', 
            padding: '40px 36px', 
            boxShadow: '0 20px 60px rgba(37, 99, 235, 0.08), 0 4px 16px rgba(0, 0, 0, 0.02)', 
            border: '1px solid rgba(255, 255, 255, 0.9)' 
          }}
        >
          {/* Auth Tab Switcher */}
          <div style={{ display: 'flex', background: '#f1f5f9', padding: '4px', borderRadius: '16px', marginBottom: '32px' }}>
            <Link 
              to="/login" 
              style={{ 
                flex: 1, 
                padding: '9px 16px', 
                borderRadius: '12px', 
                color: '#64748b', 
                fontWeight: 600, 
                textAlign: 'center', 
                fontSize: '13px', 
                textDecoration: 'none', 
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'color 0.2s' 
              }}
            >
              Sign In
            </Link>
            <div 
              style={{ 
                flex: 1, 
                padding: '9px 16px', 
                borderRadius: '12px', 
                background: '#ffffff', 
                color: '#4f46e5', 
                fontWeight: 700, 
                textAlign: 'center', 
                fontSize: '13px', 
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px'
              }}
            >
              <User size={15} color="#4f46e5" />
              <span>Create Account</span>
            </div>
          </div>

          <h1 style={{ fontSize: '26px', fontWeight: 800, marginBottom: '6px', letterSpacing: '-0.02em', color: '#0f172a' }}>
            Create an account
          </h1>
          <p style={{ color: '#64748b', marginBottom: '28px', fontSize: '13.5px', lineHeight: 1.5 }}>
            Get started with your free visual workflow workspace.
          </p>

          {/* Google Sign-In */}
          {isGoogleConfigured ? (
            <div
              style={{ position: 'relative', width: '100%', height: '46px', marginBottom: '24px' }}
              onMouseEnter={() => setIsHovered(true)}
              onMouseLeave={() => setIsHovered(false)}
            >
              <button
                type="button"
                onClick={handleGoogleClickFallback}
                disabled={googleLoading}
                style={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '10px',
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  color: '#1e293b',
                  fontSize: '14px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: isHovered ? '0 4px 14px rgba(0, 0, 0, 0.05)' : 'none',
                  transition: 'all 0.2s ease',
                }}
              >
                <GoogleIcon />
                <span>{googleLoading ? 'Signing up...' : 'Sign up with Google'}</span>
              </button>

              <div
                ref={googleBtnRef}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: '100%',
                  opacity: 0.001,
                  zIndex: 10,
                  overflow: 'hidden',
                  cursor: 'pointer',
                }}
              />
            </div>
          ) : (
            <div style={{ marginBottom: '24px', padding: '12px 16px', background: 'rgba(245, 158, 11, 0.08)', border: '1px solid rgba(245, 158, 11, 0.25)', borderRadius: '12px', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertCircle size={16} color="#d97706" style={{ flexShrink: 0 }} />
              <span style={{ fontSize: '12px', color: '#b45309' }}>
                Google login not configured. Add <code style={{ background: 'rgba(0,0,0,0.06)', padding: '1px 4px', borderRadius: '4px' }}>VITE_GOOGLE_CLIENT_ID</code> to .env.
              </span>
            </div>
          )}

          {/* Divider */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
            <div style={{ flex: 1, height: '1px', background: '#e2e8f0' }} />
            <span style={{ color: '#94a3b8', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700 }}>OR</span>
            <div style={{ flex: 1, height: '1px', background: '#e2e8f0' }} />
          </div>

          <form onSubmit={handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 700, color: '#334155' }}>Full name</label>
              <div style={{ position: 'relative' }}>
                <User size={16} color="#94a3b8" style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)' }} />
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Jane Doe"
                  style={{ 
                    background: '#f8fafc', 
                    border: '1px solid #e2e8f0', 
                    padding: '12px 16px 12px 42px', 
                    borderRadius: '12px', 
                    color: '#0f172a', 
                    outline: 'none', 
                    width: '100%', 
                    boxSizing: 'border-box', 
                    fontSize: '14px',
                    transition: 'border-color 0.2s, box-shadow 0.2s'
                  }}
                  onFocus={(e) => {
                    e.target.style.borderColor = '#6366f1'
                    e.target.style.boxShadow = '0 0 0 3px rgba(99, 102, 241, 0.15)'
                  }}
                  onBlur={(e) => {
                    e.target.style.borderColor = '#e2e8f0'
                    e.target.style.boxShadow = 'none'
                  }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 700, color: '#334155' }}>Email address</label>
              <div style={{ position: 'relative' }}>
                <Mail size={16} color="#94a3b8" style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)' }} />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@company.com"
                  style={{ 
                    background: '#f8fafc', 
                    border: '1px solid #e2e8f0', 
                    padding: '12px 16px 12px 42px', 
                    borderRadius: '12px', 
                    color: '#0f172a', 
                    outline: 'none', 
                    width: '100%', 
                    boxSizing: 'border-box', 
                    fontSize: '14px',
                    transition: 'border-color 0.2s, box-shadow 0.2s'
                  }}
                  onFocus={(e) => {
                    e.target.style.borderColor = '#6366f1'
                    e.target.style.boxShadow = '0 0 0 3px rgba(99, 102, 241, 0.15)'
                  }}
                  onBlur={(e) => {
                    e.target.style.borderColor = '#e2e8f0'
                    e.target.style.boxShadow = 'none'
                  }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 700, color: '#334155' }}>Password</label>
              <div style={{ position: 'relative' }}>
                <Lock size={16} color="#94a3b8" style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)' }} />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  style={{ 
                    width: '100%', 
                    background: '#f8fafc', 
                    border: '1px solid #e2e8f0', 
                    padding: '12px 44px 12px 42px', 
                    borderRadius: '12px', 
                    color: '#0f172a', 
                    outline: 'none', 
                    boxSizing: 'border-box', 
                    fontSize: '14px',
                    transition: 'border-color 0.2s, box-shadow 0.2s'
                  }}
                  onFocus={(e) => {
                    e.target.style.borderColor = '#6366f1'
                    e.target.style.boxShadow = '0 0 0 3px rgba(99, 102, 241, 0.15)'
                  }}
                  onBlur={(e) => {
                    e.target.style.borderColor = '#e2e8f0'
                    e.target.style.boxShadow = 'none'
                  }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{ position: 'absolute', right: '14px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: 0 }}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button 
              type="submit" 
              disabled={loading} 
              style={{ 
                width: '100%', 
                height: '48px',
                borderRadius: '12px',
                background: 'linear-gradient(135deg, #6366f1 0%, #3b82f6 100%)',
                color: '#ffffff',
                fontSize: '15px',
                fontWeight: 600,
                border: 'none',
                cursor: loading ? 'not-allowed' : 'pointer',
                opacity: loading ? 0.7 : 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                boxShadow: '0 4px 16px rgba(99, 102, 241, 0.35)',
                transition: 'all 0.2s ease',
                marginTop: '6px',
              }}
            >
              <span>{loading ? 'Creating Account...' : 'Create Account'}</span>
              <ArrowRight size={16} />
            </button>
          </form>

          <div style={{ marginTop: '28px', textAlign: 'center', color: '#64748b', fontSize: '13px' }}>
            Already have an account? <Link to="/login" style={{ color: '#4f46e5', textDecoration: 'none', fontWeight: 700 }}>Sign in</Link>
          </div>
        </div>
      </div>

    </div>
  )
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
      <path d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 01-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
      <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 009 18z" fill="#34A853"/>
      <path d="M3.964 10.71A5.41 5.41 0 013.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05"/>
      <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 00.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335"/>
    </svg>
  )
}
