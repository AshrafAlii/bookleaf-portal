/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        leaf: {
          50: '#f2f9f2',
          100: '#e0f1e1',
          200: '#c2e3c5',
          300: '#93ce99',
          400: '#5db166',
          500: '#389342',
          600: '#2a7533',
          700: '#245d2c',
          800: '#214a27',
          900: '#1d3e23',
          950: '#0c2211',
        },
        ink: {
          50: '#f7f8f7',
          100: '#eef0ef',
          200: '#d9deda',
          300: '#b4bfb6',
          400: '#859289',
          500: '#647065',
          600: '#4d584f',
          700: '#3f4741',
          800: '#343b36',
          900: '#2c322e',
          950: '#161a17',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(16,24,16,.04), 0 8px 24px -12px rgba(16,24,16,.12)',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fade-in .18s ease-out',
      },
    },
  },
  plugins: [],
}
