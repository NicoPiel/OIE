import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import './index.scss';
import App from './App.tsx';

// Configure Monaco to use the local installation and start loading it immediately
loader.config({ monaco });
loader.init().catch((err) => console.error('Failed to warm up Monaco:', err));

const queryClient = new QueryClient();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>
);
