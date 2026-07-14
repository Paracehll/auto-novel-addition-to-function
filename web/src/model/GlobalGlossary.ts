import type { Glossary } from './Glossary';

export interface GlobalGlossaryDiffItem {
  old: string | null;
  new: string | null;
}

export interface GlobalGlossaryRecord {
  date: string;
  ver: number;
  diff: { [key: string]: GlobalGlossaryDiffItem };
}

export interface GlobalGlossary {
  id: string;
  uid: string;
  name: string;
  content: Glossary;
  used: string[];
  ver: number;
  record: GlobalGlossaryRecord[];
}
