import type { GlobalGlossaryFull, GlobalGlossaryInfo, GlobalGlossaryHistory, GlobalGlossaryTerms } from '@/model/GlobalGlossary';
import { client } from './client';

const listGlobalGlossaries = () =>
  client.get('global-glossary').json<GlobalGlossaryFull[]>();

const listGlobalGlossariesInfo = (used?: boolean) =>
  client.get('global-glossary', { searchParams: used ? { used: '1' } : undefined }).json<GlobalGlossaryInfo[]>();

const getGlobalGlossary = (id: string) =>
  client.get(`global-glossary/${id}`).json<GlobalGlossaryFull>();

const getGlobalGlossaryTerms = (id: string) =>
  client.get(`global-glossary/${id}/terms`).json<GlobalGlossaryTerms>();

const getGlobalGlossaryHistory = (id: string) =>
  client.get(`global-glossary/${id}/history`).json<GlobalGlossaryHistory>();

const createGlobalGlossary = (json: {
  name: string;
  content: { [key: string]: string };
  tag: string[];
}) => client.post('global-glossary', { json }).json<GlobalGlossaryFull>();

const updateGlobalGlossary = (
  id: string,
  json: {
    name: string;
    content: { [key: string]: string };
    tag: string[];
  },
) => client.put(`global-glossary/${id}`, { json }).json<GlobalGlossaryFull>();

const deleteGlobalGlossary = (id: string) =>
  client.delete(`global-glossary/${id}`);

const deleteGlobalGlossaryRecord = (id: string, index: number) =>
  client.delete(`global-glossary/${id}/record/${index}`);

export const GlobalGlossaryApi = {
  listGlobalGlossaries,
  listGlobalGlossariesInfo,
  getGlobalGlossary,
  getGlobalGlossaryTerms,
  getGlobalGlossaryHistory,
  createGlobalGlossary,
  updateGlobalGlossary,
  deleteGlobalGlossary,
  deleteGlobalGlossaryRecord,
};
