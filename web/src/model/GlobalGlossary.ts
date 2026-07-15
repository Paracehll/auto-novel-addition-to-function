import type { Glossary } from './Glossary';

export interface GlobalGlossaryDiffItem {
  old: string | null;
  new: string | null;
}

export interface GlobalGlossaryRecord {
  date: number;
  diff: { [key: string]: GlobalGlossaryDiffItem };
}

export interface GlobalGlossary {
  id: string;
  uid: string;
  name: string;
  content: Glossary;
  used: string[];
  update: number;
  tag: string[];
  record: GlobalGlossaryRecord[];
}
