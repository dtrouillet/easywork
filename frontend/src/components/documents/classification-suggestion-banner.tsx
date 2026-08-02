"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Sparkles, Check, X } from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import type { ConfirmSuggestionRequest, DocumentClassificationSuggestionDto } from "@/lib/api/types";

interface ClassificationSuggestionBannerProps {
  documentId: string;
}

function isEmptySuggestion(suggestion: DocumentClassificationSuggestionDto): boolean {
  return (
    !suggestion.suggestedCorrespondent &&
    !suggestion.suggestedDocumentType &&
    !suggestion.suggestedDocumentDate &&
    suggestion.suggestedTags.length === 0
  );
}

function describe(suggestion: DocumentClassificationSuggestionDto): string {
  const { suggestedCorrespondent, suggestedDocumentType } = suggestion;
  if (suggestedCorrespondent && suggestedDocumentType) {
    return `This looks like a ${suggestedDocumentType.name} from ${suggestedCorrespondent.name}.`;
  }
  if (suggestedCorrespondent) {
    return `This looks like it's from ${suggestedCorrespondent.name}.`;
  }
  if (suggestedDocumentType) {
    return `This looks like a ${suggestedDocumentType.name}.`;
  }
  return "We found a possible classification for this document.";
}

/** ADR 0003: "This looks like an EDF invoice — suggested tags: Energy, Invoices. Confirm?" */
export function ClassificationSuggestionBanner({ documentId }: ClassificationSuggestionBannerProps) {
  const { data: session } = useSession();
  const queryClient = useQueryClient();

  const { data: suggestion } = useQuery({
    queryKey: ["document", documentId, "suggestion"],
    queryFn: () => documentsApi(session!.accessToken).getSuggestion(documentId),
    enabled: !!session,
    retry: false,
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["document", documentId] });
    queryClient.invalidateQueries({ queryKey: ["document", documentId, "suggestion"] });
    queryClient.invalidateQueries({ queryKey: ["documents"] });
  }

  const confirm = useMutation({
    mutationFn: () => {
      const request: ConfirmSuggestionRequest = {
        acceptCorrespondent: !!suggestion!.suggestedCorrespondent,
        acceptDocumentType: !!suggestion!.suggestedDocumentType,
        acceptDocumentDate: !!suggestion!.suggestedDocumentDate,
        acceptTagIds: suggestion!.suggestedTags.map((tag) => tag.id),
      };
      return documentsApi(session!.accessToken).confirmSuggestion(documentId, request);
    },
    onSuccess: invalidate,
  });

  const reject = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).rejectSuggestion(documentId),
    onSuccess: invalidate,
  });

  if (!suggestion || suggestion.status !== "PENDING" || isEmptySuggestion(suggestion)) {
    return null;
  }

  return (
    <div className="rounded-lg border border-primary/30 bg-primary/5 p-4 space-y-3">
      <div className="flex items-start gap-2.5">
        <Sparkles className="h-4 w-4 text-primary shrink-0 mt-0.5" />
        <div className="space-y-1 min-w-0">
          <p className="text-sm">{describe(suggestion)}</p>
          {suggestion.suggestedTags.length > 0 && (
            <p className="text-sm text-muted-foreground">
              Suggested tags: {suggestion.suggestedTags.map((tag) => tag.name).join(", ")}
            </p>
          )}
          {suggestion.suggestedDocumentDate && (
            <p className="text-sm text-muted-foreground">
              Suggested date: {suggestion.suggestedDocumentDate}
            </p>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={() => confirm.mutate()}
          disabled={confirm.isPending || reject.isPending}
          className="flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-md bg-primary text-primary-foreground disabled:opacity-50"
        >
          <Check className="h-3.5 w-3.5" /> Confirm
        </button>
        <button
          onClick={() => reject.mutate()}
          disabled={confirm.isPending || reject.isPending}
          className="flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-md border border-border text-muted-foreground hover:bg-accent transition-colors disabled:opacity-50"
        >
          <X className="h-3.5 w-3.5" /> Dismiss
        </button>
      </div>
    </div>
  );
}
