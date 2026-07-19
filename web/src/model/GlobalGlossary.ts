import type { Glossary } from './Glossary';

export interface GlobalGlossaryDiffItem {
  old: string | null;
  new: string | null;
}

export interface GlobalGlossaryRecord {
  date: number;
  diff: { [key: string]: GlobalGlossaryDiffItem };
  by: string;
}

export interface GlobalGlossaryTerms {
  id: string;
  terms: Glossary;
  version: number;
}

export interface GlobalGlossaryFull {
  id: string;
  name: string;
  terms: Glossary;
  termsCount: number;
  used: string[];
  usedCount: number;
  update: number;
  tag: string[];
  record: GlobalGlossaryRecord[];
  version: number;
}

// For backward compatibility or general reference
export type GlobalGlossary = GlobalGlossaryFull;
