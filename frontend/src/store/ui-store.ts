import { create } from "zustand";

export type UploadQueueItemStatus = "pending" | "uploading" | "done" | "error";

export interface UploadQueueItem {
  id: string;
  file: File;
  status: UploadQueueItemStatus;
  progress: number;
  error?: string;
}

interface UiState {
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  uploadDialogOpen: boolean;
  setUploadDialogOpen: (open: boolean) => void;
  uploadQueue: UploadQueueItem[];
  addToUploadQueue: (files: File[]) => void;
  updateUploadItem: (id: string, patch: Partial<UploadQueueItem>) => void;
  removeFromUploadQueue: (id: string) => void;
  clearUploadQueue: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  uploadDialogOpen: false,
  setUploadDialogOpen: (open) => set({ uploadDialogOpen: open }),
  uploadQueue: [],
  addToUploadQueue: (files) =>
    set((s) => ({
      uploadQueue: [
        ...s.uploadQueue,
        ...files.map((file) => ({
          id: crypto.randomUUID(),
          file,
          status: "pending" as const,
          progress: 0,
        })),
      ],
    })),
  updateUploadItem: (id, patch) =>
    set((s) => ({
      uploadQueue: s.uploadQueue.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    })),
  removeFromUploadQueue: (id) =>
    set((s) => ({ uploadQueue: s.uploadQueue.filter((item) => item.id !== id) })),
  clearUploadQueue: () => set({ uploadQueue: [] }),
}));
