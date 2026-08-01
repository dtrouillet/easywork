import { FileText, FileImage, File } from "lucide-react";

export function MimeIcon({ mime, className }: { mime: string; className?: string }) {
  const cls = className ?? "h-5 w-5";
  if (mime.startsWith("image/")) return <FileImage className={cls} />;
  if (mime === "application/pdf") return <FileText className={cls} />;
  return <File className={cls} />;
}
