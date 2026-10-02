/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#173B5F',
          dark: '#0D2740',
          light: '#25527D',
          soft: '#EEF3F8',
        },
        accent: {
          DEFAULT: '#3B82F6',
          dark: '#2563EB',
          light: '#DBEAFE',
        },
        success: {
          DEFAULT: '#16A34A',
          dark: '#15803D',
          light: '#DCFCE7',
        },
        warning: {
          DEFAULT: '#F59E0B',
          dark: '#B45309',
          light: '#FEF3C7',
        },
        danger: {
          DEFAULT: '#DC2626',
          dark: '#B91C1C',
          light: '#FEE2E2',
        },
        surface: {
          DEFAULT: '#FFFFFF',
          sunken: '#FAFBFD',
        },
        background: '#F6F8FB',
        ink: {
          DEFAULT: '#172033',
          muted: '#667085',
          subtle: '#98A2B3',
        },
        border: {
          DEFAULT: '#E4E7EC',
          strong: '#D0D5DD',
        },
      },
      fontFamily: {
        sans: ['Inter', 'Manrope', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        display: ['Manrope', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        'display-lg': ['3.25rem', { lineHeight: '1.05', letterSpacing: '-0.03em', fontWeight: '700' }],
        'display': ['2.5rem', { lineHeight: '1.1', letterSpacing: '-0.025em', fontWeight: '700' }],
        'title': ['1.75rem', { lineHeight: '1.2', letterSpacing: '-0.02em', fontWeight: '700' }],
        'headline': ['1.25rem', { lineHeight: '1.35', letterSpacing: '-0.01em', fontWeight: '600' }],
        'money': ['2rem', { lineHeight: '1.15', letterSpacing: '-0.02em', fontWeight: '700' }],
        'label': ['0.8125rem', { lineHeight: '1.4', letterSpacing: '0.01em', fontWeight: '600' }],
        'caption': ['0.75rem', { lineHeight: '1.45', letterSpacing: '0.02em' }],
      },
      spacing: {
        18: '4.5rem',
        22: '5.5rem',
        26: '6.5rem',
        30: '7.5rem',
      },
      borderRadius: {
        DEFAULT: '6px',
        md: '8px',
        lg: '10px',
        xl: '14px',
      },
      boxShadow: {
        card: '0 1px 2px 0 rgba(16, 24, 40, 0.04), 0 1px 3px 0 rgba(16, 24, 40, 0.06)',
        raised: '0 4px 12px -2px rgba(16, 24, 40, 0.08), 0 2px 4px -2px rgba(16, 24, 40, 0.04)',
        overlay: '0 12px 32px -8px rgba(13, 39, 64, 0.22)',
        focus: '0 0 0 3px rgba(59, 130, 246, 0.25)',
      },
      keyframes: {
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'slide-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'scale-in': {
          from: { opacity: '0', transform: 'scale(0.97)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'check-stroke': {
          from: { strokeDashoffset: '48' },
          to: { strokeDashoffset: '0' },
        },
      },
      animation: {
        'fade-in': 'fade-in 160ms ease-out',
        'slide-up': 'slide-up 220ms ease-out',
        'scale-in': 'scale-in 140ms ease-out',
        'check-stroke': 'check-stroke 520ms ease-out 120ms both',
      },
      screens: {
        xs: '420px',
      },
    },
  },
  plugins: [],
}
