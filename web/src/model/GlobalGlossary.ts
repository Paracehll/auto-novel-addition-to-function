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

export interface GlobalGlossaryInfo {
  id: string;
  name: string;
  termsCount: number;
  usedCount: number;
  update: number;
  tag: string[];
  version: number;
  used?: string[];
}

export interface GlobalGlossaryHistory {
  id: string;
  record: GlobalGlossaryRecord[];
  update: number;
  version: number;
}
