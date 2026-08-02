import { useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { useUiStore, type UploadQueueItem } from "@/store/ui-store";

const MAX_CONCURRENT_UPLOADS = 3;

function friendlyErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 413) return "File too large";
    if (error.status === 415) return "Unsupported file type";
    if (error.status === 401 || error.status === 403) return "Access denied";
  }
  return "Upload failed";
}

export function useDocumentUpload() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const uploadQueue = useUiStore((s) => s.uploadQueue);
  const addToUploadQueue = useUiStore((s) => s.addToUploadQueue);
  const updateUploadItem = useUiStore((s) => s.updateUploadItem);
  const removeFromUploadQueue = useUiStore((s) => s.removeFromUploadQueue);
  const clearUploadQueue = useUiStore((s) => s.clearUploadQueue);
  const controllers = useRef(new Map<string, AbortController>());

  function startUpload(item: UploadQueueItem, token: string) {
    const controller = new AbortController();
    controllers.current.set(item.id, controller);
    updateUploadItem(item.id, { status: "uploading", progress: 0, error: undefined });

    documentsApi(token)
      .uploadWithProgress(
        item.file,
        (percent) => updateUploadItem(item.id, { progress: percent }),
        controller.signal
      )
      .then(() => {
        updateUploadItem(item.id, { status: "done", progress: 100 });
        toast.success(`${item.file.name} uploaded`);
      })
      .catch((error: unknown) => {
        updateUploadItem(item.id, { status: "error", error: friendlyErrorMessage(error) });
        toast.error(`${item.file.name} : ${friendlyErrorMessage(error)}`);
      })
      .finally(() => {
        controllers.current.delete(item.id);
        runScheduler();
      });
  }

  function runScheduler() {
    const token = session?.accessToken;
    if (!token) return;

    const queue = useUiStore.getState().uploadQueue;
    const uploading = queue.filter((i) => i.status === "uploading").length;
    const pending = queue.filter((i) => i.status === "pending");
    const slots = Math.max(0, MAX_CONCURRENT_UPLOADS - uploading);

    pending.slice(0, slots).forEach((item) => startUpload(item, token));

    if (queue.length > 0 && queue.every((i) => i.status === "done" || i.status === "error")) {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
    }
  }

  function addFiles(files: File[]) {
    addToUploadQueue(files);
    runScheduler();
  }

  function removeItem(id: string) {
    controllers.current.get(id)?.abort();
    controllers.current.delete(id);
    removeFromUploadQueue(id);
  }

  function retryItem(id: string) {
    updateUploadItem(id, { status: "pending", progress: 0, error: undefined });
    runScheduler();
  }

  function clearQueue() {
    controllers.current.forEach((controller) => controller.abort());
    controllers.current.clear();
    clearUploadQueue();
  }

  return { queue: uploadQueue, addFiles, removeItem, retryItem, clearQueue };
}
